# Deploying to AWS

Target: **ECS Fargate** (3 services) behind an **ALB**, **RDS PostgreSQL**, **Amazon MSK** (Kafka), images in **ECR**. Account APIs run inside claims-service; they are not a fourth ECS service.

> These steps were written but **not executed** (no AWS account available while authoring). Treat as a runbook to verify; adjust names/regions. Costs money – tear down when done.

```
Internet ─► ALB (80/443) ─► api-gateway (Fargate :8090)
                               ├─► claims-service    (Fargate :8091) ─► RDS claims_db ─► MSK
                               └─► reporting-service (Fargate :8092) ─► RDS reporting_db ◄─ MSK
 (gateway → services via ECS Service Connect / Cloud Map DNS; all in private subnets)
```

Variables used below:

```bash
export AWS_REGION=ap-southeast-1          # Singapore
export AWS_ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
export ECR=$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com
```

## 1. Push images to ECR

```bash
./deploy/aws/push-images.sh        # creates the 3 repos, builds, tags :latest, pushes
```

## 2. Network

Use a VPC with 2+ private subnets (for Fargate, RDS, MSK) and 2 public subnets (ALB). Security groups:

| SG | Inbound |
|---|---|
| `alb-sg` | 80/443 from the internet |
| `gateway-sg` | 8090 from `alb-sg` |
| `svc-sg` (claims + reporting) | 8091/8092 from `gateway-sg` |
| `rds-sg` | 5432 from `svc-sg` |
| `msk-sg` | 9094 (TLS) from `svc-sg` |

## 3. RDS PostgreSQL

```bash
aws rds create-db-instance --db-instance-identifier claims-pg --engine postgres --engine-version 16 \
  --db-instance-class db.t4g.micro --allocated-storage 20 \
  --master-username postgres --manage-master-user-password \
  --db-subnet-group-name <private-subnet-group> --vpc-security-group-ids <rds-sg> --no-publicly-accessible
```

Then create the per-service databases/users (from a bastion/Cloud9/SSM session inside the VPC), using strong passwords instead of the dev ones in `deploy/postgres/init.sql`:

```sql
CREATE USER claims WITH PASSWORD '<secret>';    CREATE DATABASE claims_db    OWNER claims;
CREATE USER reporting WITH PASSWORD '<secret>'; CREATE DATABASE reporting_db OWNER reporting;
```

Store DB credentials in **Secrets Manager** (`claims/db`, `reporting/db`). Claims-service V2 Flyway migration creates the accounts table; claims V1 and reporting migrations create their existing tables.

## 4. MSK (Kafka)

Create an MSK **provisioned** cluster (3 × `kafka.t3.small`, 3 AZs, TLS in transit, unauthenticated-within-VPC for this exercise; use IAM/SASL for production). Note the **TLS bootstrap brokers** (port 9094):

```bash
aws kafka get-bootstrap-brokers --cluster-arn <arn> --query BootstrapBrokerStringTls
```

The claims-service auto-creates topic `claim-events` (3 partitions). With 3 brokers set `CLAIM_EVENTS_REPLICAS=3`.

## 5. ECS cluster, Service Connect, task definitions

```bash
aws ecs create-cluster --cluster-name claims-platform \
  --service-connect-defaults namespace=claims.local
```

Register one task definition per service (template: `deploy/aws/task-definition.template.json` – replace the `__PLACEHOLDERS__`). Environment per service:

| Service | Environment and Secrets Manager values |
|---|---|
| claims-service | `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/claims_db`, `DB_USER=claims`, `DB_PASSWORD` (secret), `KAFKA_BOOTSTRAP_SERVERS=<tls brokers>`, `SPRING_KAFKA_SECURITY_PROTOCOL=SSL`, `CLAIM_EVENTS_REPLICAS=3`, `JWT_SECRET` (shared secret), `BOOTSTRAP_MANAGER_EMAIL`, `BOOTSTRAP_MANAGER_NAME`, and `BOOTSTRAP_MANAGER_PASSWORD` (secret). |
| reporting-service | `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/reporting_db`, `DB_USER=reporting`, `DB_PASSWORD` (secret), `KAFKA_BOOTSTRAP_SERVERS=<tls brokers>`, `SPRING_KAFKA_SECURITY_PROTOCOL=SSL`, and the same `JWT_SECRET` value as claims-service. |
| api-gateway | `CLAIMS_SERVICE_URL=http://claims-service:8091`, `REPORTING_SERVICE_URL=http://reporting-service:8092`. |

Store the same randomly generated `JWT_SECRET` (at least 32 bytes) in Secrets Manager and inject it into both claims-service and reporting-service. Do not use the Compose development secret. The first manager is inserted by claims-service only when both bootstrap email and password are configured; after startup, create officer accounts from the manager UI. Set the `JWT_SECRET` and manager password as ECS task secrets, not plaintext task environment values.

**Before launch:** the service `application.yml` files contain local fallback values for JWT, database, and bootstrap-manager credentials. Explicitly configure production values for every service; never let a task start using those fallbacks. Restrict Swagger/OpenAPI access or disable it in production.

Sizing to start: 0.5 vCPU / 1 GB per service. The task execution role needs ECR pull, CloudWatch Logs and `secretsmanager:GetSecretValue`.

Create three ECS services (Fargate, private subnets, `desiredCount` 2 for claims/gateway, 1 for reporting). Use container ports 8090, 8091 and 8092 to match Spring's `server.port` settings. Enable **Service Connect** with aliases `claims-service:8091` and `reporting-service:8092` so the gateway resolves them by name. The generic task template's `__PORT__` must be set to the matching container port for each service.

> Reporting-service scaling note: Kafka consumer parallelism is capped by partition count (3), and instances share the `reporting-service` consumer group.

## 6. ALB

- Target group `gateway-tg` (type `ip`, port 8090), health check path `/actuator/health`.
- Listener 80 (add 443 + ACM certificate for real use) → `gateway-tg`.
- Attach `gateway-tg` to the api-gateway ECS service.

## 7. React website and public domain

The React app is a static Vite build; it is not included in the three backend ECS services. Host it in a private S3 bucket and serve it through CloudFront. Use separate site and API hostnames, for example `app.example.com` and `api.example.com`:

```text
Browser ── HTTPS ──► CloudFront ──► private S3 bucket (React assets)
   │
   └── HTTPS API calls ──► api.example.com ──► ALB ──► api-gateway (ECS)
```

1. Register the domain or create a Route 53 hosted zone for it.
2. Request a public ACM certificate for `app.example.com` in **us-east-1** for CloudFront. Request a separate certificate for `api.example.com` in the ALB's region. Complete DNS validation.
3. Create a private S3 bucket for the built site. Do not enable public bucket access or use the S3 website endpoint. Create a CloudFront distribution with the S3 bucket as origin and Origin Access Control (OAC) so only CloudFront can read the assets.
4. Add the `app.example.com` alternate domain to CloudFront and attach its certificate. Configure Route 53 `A/AAAA` alias records for `app.example.com` to the distribution. Redirect viewer HTTP to HTTPS.
5. Add an HTTPS listener on the ALB using the `api.example.com` certificate, and create a Route 53 alias from `api.example.com` to the ALB. Redirect ALB HTTP to HTTPS. Keep only the gateway behind the ALB; claims-service and reporting-service remain private.
6. Restrict gateway CORS to `https://app.example.com` (including the `Authorization` and `Content-Type` headers). The current gateway configuration allows any origin, so change that before launch.
7. Build the frontend with the production API URL and upload the generated `dist` directory:

   ```bash
   cd frontend
   npm ci
   VITE_API_BASE=https://api.example.com npm run build
   export SITE_BUCKET=your-globally-unique-site-bucket
   export CLOUDFRONT_DISTRIBUTION_ID=your-distribution-id
   aws s3 sync dist/ "s3://${SITE_BUCKET}/" --delete
   aws cloudfront create-invalidation --distribution-id "$CLOUDFRONT_DISTRIBUTION_ID" --paths '/*'
   ```

   `VITE_API_BASE` is a public URL, not a secret. Do not put credentials or tokens in frontend build variables. Set CloudFront caching so HTML is revalidated promptly and fingerprinted assets can be cached for a long time. If the frontend later adds client-side routes, configure CloudFront's not-found handling to serve `/index.html` for those routes.

## 8. Verify

```bash
curl https://api.example.com/actuator/health
open https://app.example.com
```

Set `https://api.example.com` as the base URL in `postman/aws.postman_environment.json`. Set the manager email/password to the deployed bootstrap account and use unique test emails/passwords for the two officers and claimants. Run **0a. Authentication setup** once for those accounts, then run the lifecycle and manager report folders. Avoid committing deployment credentials to the environment file. Keep Swagger/OpenAPI private or disabled in production.

## 9. Production hardening (not done)

HTTPS only; WAF + rate limiting; replace the current shared-key JWT with an OIDC provider and key rotation; add password reset/MFA and stronger session/token revocation; hide Swagger UI behind auth or disable; RDS Multi-AZ + backups; MSK IAM auth; autoscaling policies; CloudWatch alarms (5xx, consumer lag, RDS CPU); CI/CD (GitHub Actions → ECR → `aws ecs update-service --force-new-deployment`); IaC (Terraform/CDK) instead of CLI steps.

## 10. Tear down

Delete ECS services → cluster → ALB/target group → MSK → RDS (take snapshot if needed) → ECR repos → secrets.
