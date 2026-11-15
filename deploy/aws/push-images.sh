#!/usr/bin/env bash
# Build the three service images and push them to ECR.
# Usage: AWS_REGION=ap-southeast-1 ./deploy/aws/push-images.sh [tag]
set -euo pipefail

REGION="${AWS_REGION:-ap-southeast-1}"
TAG="${1:-latest}"
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
REGISTRY="${ACCOUNT_ID}.dkr.ecr.${REGION}.amazonaws.com"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

aws ecr get-login-password --region "$REGION" | docker login --username AWS --password-stdin "$REGISTRY"

for svc in claims-service reporting-service api-gateway; do
  aws ecr describe-repositories --repository-names "$svc" --region "$REGION" >/dev/null 2>&1 \
    || aws ecr create-repository --repository-name "$svc" --region "$REGION" \
         --image-scanning-configuration scanOnPush=true >/dev/null
  # Fargate is x86_64 by default; build for it explicitly (matters when building on Apple Silicon)
  docker build --platform linux/amd64 -t "${REGISTRY}/${svc}:${TAG}" "${ROOT}/${svc}"
  docker push "${REGISTRY}/${svc}:${TAG}"
  echo "pushed ${REGISTRY}/${svc}:${TAG}"
done
