-- Payment reminders can be switched off per center.
alter table center_settings add column payment_reminders_enabled boolean not null default true;
