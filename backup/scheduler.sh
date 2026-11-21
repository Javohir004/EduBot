#!/bin/sh
# Runs backup.sh every day at BACKUP_TIME (HH:MM, container time zone TZ).
set -eu
: "${BACKUP_TIME:=03:00}"
echo "backup scheduler: daily at ${BACKUP_TIME} (${TZ:-UTC}), keeping ${BACKUP_RETENTION_DAYS:-14} days"
while true; do
  now=$(date +%s)
  next=$(date -d "$BACKUP_TIME" +%s)
  [ "$next" -gt "$now" ] || next=$((next + 86400))
  sleep $((next - now))
  backup.sh || echo "backup FAILED at $(date -Iseconds)" >&2
done
