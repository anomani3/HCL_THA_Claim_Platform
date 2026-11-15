-- One database + one owner per service: services never share tables (database-per-service).
CREATE USER claims WITH PASSWORD 'claims';
CREATE DATABASE claims_db OWNER claims;

CREATE USER reporting WITH PASSWORD 'reporting';
CREATE DATABASE reporting_db OWNER reporting;
