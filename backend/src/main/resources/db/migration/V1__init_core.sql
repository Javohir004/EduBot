-- Core identity, tenancy root and subscription tables.
-- Conventions: UUID v7 keys, timestamptz in UTC, enums stored as checked varchar.

create table app_user
(
    id             uuid primary key,
    telegram_id    bigint       not null,
    first_name     varchar(128) not null,
    last_name      varchar(128),
    username       varchar(64),
    language_code  varchar(16),
    verified_phone varchar(16),
    platform_role  varchar(32),
    bot_blocked    boolean      not null default false,
    created_at     timestamptz  not null,
    updated_at     timestamptz  not null,
    constraint uq_app_user_telegram_id unique (telegram_id),
    constraint chk_app_user_platform_role check (platform_role in ('SUPER_ADMIN'))
);

create table education_center
(
    id               uuid primary key,
    name             varchar(200) not null,
    phone            varchar(16),
    address          varchar(300),
    timezone         varchar(64)  not null default 'Asia/Tashkent',
    currency         varchar(3)   not null default 'UZS',
    default_language varchar(16)  not null default 'uz',
    status           varchar(16)  not null,
    created_at       timestamptz  not null,
    updated_at       timestamptz  not null,
    constraint chk_center_status check (status in ('PENDING', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'))
);

create table center_settings
(
    center_id                     uuid primary key references education_center (id),
    payment_due_day               smallint    not null default 5,
    absence_notifications_enabled boolean     not null default true,
    quiet_hours_start             time        not null default '22:00',
    quiet_hours_end               time        not null default '08:00',
    updated_at                    timestamptz not null,
    constraint chk_settings_due_day check (payment_due_day between 1 and 28)
);

create table center_membership
(
    id         uuid primary key,
    center_id  uuid        not null references education_center (id),
    user_id    uuid        not null references app_user (id),
    role       varchar(16) not null,
    status     varchar(16) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uq_membership_center_user_role unique (center_id, user_id, role),
    constraint uq_membership_center_id unique (center_id, id),
    constraint chk_membership_role check (role in ('OWNER', 'ADMIN', 'TEACHER', 'PARENT')),
    constraint chk_membership_status check (status in ('ACTIVE', 'REVOKED'))
);

create index ix_membership_user on center_membership (user_id);

create table subscription_plan
(
    id            uuid primary key,
    code          varchar(32)    not null,
    name          varchar(64)    not null,
    monthly_price numeric(15, 2) not null,
    currency      varchar(3)     not null default 'UZS',
    active        boolean        not null default true,
    public_plan   boolean        not null default true,
    sort_order    int            not null default 0,
    created_at    timestamptz    not null,
    updated_at    timestamptz    not null,
    constraint uq_plan_code unique (code),
    constraint chk_plan_price check (monthly_price >= 0)
);

-- Entitlement values are numeric; boolean features use 0/1.
create table plan_entitlement
(
    plan_id         uuid        not null references subscription_plan (id),
    entitlement_key varchar(64) not null,
    value           bigint      not null,
    primary key (plan_id, entitlement_key)
);

create table entitlement_override
(
    center_id       uuid        not null references education_center (id),
    entitlement_key varchar(64) not null,
    value           bigint      not null,
    primary key (center_id, entitlement_key)
);

create table subscription
(
    id          uuid primary key,
    center_id   uuid        not null references education_center (id),
    plan_id     uuid        not null references subscription_plan (id),
    status      varchar(16) not null,
    starts_at   timestamptz not null,
    ends_at     timestamptz not null,
    grace_until timestamptz,
    created_by  uuid references app_user (id),
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint uq_subscription_center_id unique (center_id, id),
    constraint chk_subscription_status check (status in ('TRIAL', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    constraint chk_subscription_period check (ends_at > starts_at),
    constraint chk_subscription_grace check (grace_until is null or grace_until >= ends_at)
);

-- At most one current subscription per center.
create unique index uq_subscription_current on subscription (center_id) where status in ('TRIAL', 'ACTIVE');
