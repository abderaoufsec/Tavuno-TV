#!/bin/sh
set -eu

export PGPASSWORD="$POSTGRES_PASSWORD"

psql --username "$POSTGRES_USER" --dbname postgres --set=ON_ERROR_STOP=1 \
  --set=dispatcharr_password="$DISPATCHARR_POSTGRES_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE dispatcharr LOGIN PASSWORD %L', :'dispatcharr_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'dispatcharr')
\gexec

SELECT 'CREATE DATABASE dispatcharr OWNER dispatcharr'
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = 'dispatcharr')
\gexec
SQL
