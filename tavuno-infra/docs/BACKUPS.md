# PostgreSQL Backups

This document describes the backup and restore procedures for the Tavuno PostgreSQL database.

## Backup Script

A backup script is provided at `tavuno-infra/scripts/backup_postgres.sh`. This script:

- Creates timestamped compressed SQL dumps using `pg_dump`
- Stores backups in the `tavuno-infra/backups/` directory
- Automatically deletes backups older than the retention period (default: 7 days)
- Uses the same connection configuration as defined in `docker-compose.yml`

### Configuration

The backup script reads configuration from environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `POSTGRES_HOST` | `postgres` | PostgreSQL host |
| `POSTGRES_PORT` | `5432` | PostgreSQL port |
| `POSTGRES_DB` | `tavuno` | Database name |
| `POSTGRES_USER` | `tavuno` | Database user |
| `POSTGRES_PASSWORD` | (required) | Database password |
| `BACKUP_DIR` | `./backups` | Backup directory path |
| `RETENTION_DAYS` | `7` | Number of days to retain backups |

### Running Backups Manually

#### From the host machine:

```bash
cd tavuno-infra
export POSTGRES_PASSWORD="your_password"
./scripts/backup_postgres.sh
```

#### From within the PostgreSQL container:

```bash
docker exec -it tavuno-postgres bash
# Inside container:
export POSTGRES_PASSWORD="your_password"
/scripts/backup_postgres.sh
```

### Scheduled Backups (Cron)

To schedule automatic daily backups, add a cron job:

```bash
# Edit crontab
crontab -e

# Add daily backup at 2 AM
0 2 * * * cd /path/to/tavuno-infra && export POSTGRES_PASSWORD="your_password" && ./scripts/backup_postgres.sh >> /var/log/tavuno-backup.log 2>&1
```

Alternatively, use systemd timers or your preferred scheduling method.

## Restore Procedure

### From a compressed backup file:

```bash
# Decompress the backup
gunzip -c backups/tavuno_postgres_backup_YYYYMMDD_HHMMSS.sql.gz > restore.sql

# Restore to database
PGPASSWORD="your_password" psql \
    -h postgres \
    -p 5432 \
    -U tavuno \
    -d tavuno \
    -f restore.sql
```

### From within the PostgreSQL container:

```bash
# Copy backup into container
docker cp backups/tavuno_postgres_backup_YYYYMMDD_HHMMSS.sql.gz tavuno-postgres:/tmp/

# Access container
docker exec -it tavuno-postgres bash

# Inside container:
gunzip -c /tmp/tavuno_postgres_backup_YYYYMMDD_HHMMSS.sql.gz | psql -U tavuno -d tavuno
```

### Using pg_restore (for custom-format backups):

If you modify the backup script to use custom format (`--format=custom`), restore with:

```bash
pg_restore -h postgres -U tavuno -d tavuno backup.dump
```

## Backup File Naming

Backups are named with the following pattern:

```
tavuno_postgres_backup_YYYYMMDD_HHMMSS.sql.gz
```

Example: `tavuno_postgres_backup_20260919_020000.sql.gz`

## Retention Policy

By default, backups are retained for 7 days. This can be changed by setting the `RETENTION_DAYS` environment variable:

```bash
export RETENTION_DAYS=14
./scripts/backup_postgres.sh
```

## Backup Directory

The backup directory (`tavuno-infra/backups/`) is excluded from version control via `.gitignore` to prevent accidental commit of sensitive data.

## Important Notes

- **Password Security**: Never hardcode passwords in scripts or cron jobs. Use environment variables or a secure secrets manager.
- **Testing Backups**: Regularly test restore procedures in a non-production environment to ensure backups are valid.
- **Offsite Storage**: For production, consider copying backups to offsite storage (S3, GCS, etc.) for disaster recovery.
- **Backup Frequency**: Adjust backup frequency based on your data change rate and RPO (Recovery Point Objective) requirements.
- **Monitoring**: Monitor backup execution and send alerts on failure.

## Docker-Integrated Backup (Optional)

If you prefer to run backups as a Docker service, you can add a backup service to `docker-compose.yml`. However, the manual/cron-based approach provided here is simpler and more flexible for most use cases.
