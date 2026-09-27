#!/bin/bash
# PostgreSQL backup script for Tavuno
# Creates timestamped compressed backups and handles retention

set -euo pipefail

# Configuration - override via environment variables
POSTGRES_HOST="${POSTGRES_HOST:-postgres}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_DB="${POSTGRES_DB:-tavuno}"
POSTGRES_USER="${POSTGRES_USER:-tavuno}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD}"
BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"

# Create backup directory if it doesn't exist
mkdir -p "${BACKUP_DIR}"

# Generate timestamp
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/tavuno_postgres_backup_${TIMESTAMP}.sql.gz"

echo "Starting PostgreSQL backup at $(date)"
echo "Host: ${POSTGRES_HOST}:${POSTGRES_PORT}"
echo "Database: ${POSTGRES_DB}"

# Run pg_dump and compress
PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
    -h "${POSTGRES_HOST}" \
    -p "${POSTGRES_PORT}" \
    -U "${POSTGRES_USER}" \
    -d "${POSTGRES_DB}" \
    --format=plain \
    --no-owner \
    --no-acl \
    --verbose \
    2>&1 | gzip > "${BACKUP_FILE}"

if [ $? -eq 0 ]; then
    echo "Backup completed successfully: ${BACKUP_FILE}"
    
    # Display backup size
    BACKUP_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
    echo "Backup size: ${BACKUP_SIZE}"
else
    echo "Backup failed!"
    rm -f "${BACKUP_FILE}"
    exit 1
fi

# Clean up old backups
echo "Cleaning up backups older than ${RETENTION_DAYS} days..."
find "${BACKUP_DIR}" -name "tavuno_postgres_backup_*.sql.gz" -type f -mtime +${RETENTION_DAYS} -delete

# List remaining backups
echo "Current backups:"
ls -lh "${BACKUP_DIR}"/tavuno_postgres_backup_*.sql.gz 2>/dev/null || echo "No backups found"

echo "Backup process completed at $(date)"
