#!/bin/sh
# Restores an encrypted backup into PGDATABASE, replacing the existing objects in one transaction.
# Usage: restore.sh <backups/edubot-....dump.age> <age private key file>
# Stop the backend first so nothing writes during the restore (see docs/deploy.md).
set -eu -o pipefail
file=${1:?usage: restore.sh <backup.dump.age> <age-key-file>}
key=${2:?usage: restore.sh <backup.dump.age> <age-key-file>}
age --decrypt -i "$key" "$file" \
  | pg_restore --clean --if-exists --no-owner --no-privileges --single-transaction --dbname "$PGDATABASE"
echo "restore ok: $file"
