#!/bin/sh
# One encrypted backup into /backups, then deletes backups older than BACKUP_RETENTION_DAYS.
# Connection: PGHOST, PGDATABASE, PGUSER, PGPASSWORD. BACKUP_AGE_RECIPIENT: one or more age public
# keys (space-separated); only the matching private key (kept off the server) can decrypt.
set -eu -o pipefail
: "${BACKUP_AGE_RECIPIENT:?BACKUP_AGE_RECIPIENT is not set}"
umask 077
dir=/backups
name="edubot-$(date +%Y%m%d-%H%M%S).dump.age"
tmp="$dir/.$name.partial"
trap 'rm -f "$tmp"' EXIT

set --
for recipient in $BACKUP_AGE_RECIPIENT; do set -- "$@" -r "$recipient"; done

pg_dump --format=custom --compress=6 | age --encrypt "$@" > "$tmp"
mv "$tmp" "$dir/$name"
find "$dir" -name 'edubot-*.dump.age' -type f -mtime +"${BACKUP_RETENTION_DAYS:-14}" -delete
echo "backup ok: $name ($(du -h "$dir/$name" | cut -f1))"
