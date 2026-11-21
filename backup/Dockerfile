# Daily encrypted PostgreSQL backups: pg_dump (custom format, compressed) | age -> /backups.
FROM postgres:18-alpine
RUN apk add --no-cache age tzdata
COPY backup.sh restore.sh scheduler.sh /usr/local/bin/
RUN chmod 755 /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/scheduler.sh
ENTRYPOINT []
CMD ["scheduler.sh"]
