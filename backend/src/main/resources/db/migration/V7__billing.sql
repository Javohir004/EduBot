-- Tuition billing: monthly charges per enrollment, payments, allocations of payments to charges,
-- and an append-only audit log for money corrections.

create table tuition_charge
(
    id            uuid primary key,
    center_id     uuid           not null,
    enrollment_id uuid           not null,
    student_id    uuid           not null,
    group_id      uuid           not null,
    period_start  date           not null,
    period_end    date           not null,
    amount        numeric(15, 2) not null,
    paid_amount   numeric(15, 2) not null default 0,
    due_date      date           not null,
    status        varchar(16)    not null,
    created_at    timestamptz    not null,
    updated_at    timestamptz    not null,
    constraint uq_charge_center_id unique (center_id, id),
    constraint fk_charge_enrollment foreign key (center_id, enrollment_id) references enrollment (center_id, id),
    constraint fk_charge_student foreign key (center_id, student_id) references student (center_id, id),
    constraint fk_charge_group foreign key (center_id, group_id) references study_group (center_id, id),
    -- One charge per enrollment and month: generation is idempotent.
    constraint uq_charge_period unique (enrollment_id, period_start),
    constraint chk_charge_period check (period_end >= period_start),
    constraint chk_charge_amount check (amount >= 0),
    constraint chk_charge_paid check (paid_amount >= 0 and paid_amount <= amount),
    constraint chk_charge_status check (status in ('OPEN', 'VOID'))
);

create index ix_charge_student on tuition_charge (center_id, student_id, period_start);
create index ix_charge_open on tuition_charge (center_id, period_start) where status = 'OPEN';

create table payment
(
    id              uuid primary key,
    center_id       uuid           not null,
    student_id      uuid           not null,
    amount          numeric(15, 2) not null,
    method          varchar(16)    not null,
    paid_at         timestamptz    not null,
    received_by     uuid           not null references app_user (id),
    note            varchar(500),
    status          varchar(16)    not null,
    voided_at       timestamptz,
    voided_by       uuid references app_user (id),
    void_reason     varchar(500),
    idempotency_key varchar(100),
    provider        varchar(32),
    provider_txn_id varchar(100),
    created_at      timestamptz    not null,
    constraint uq_payment_center_id unique (center_id, id),
    constraint fk_payment_student foreign key (center_id, student_id) references student (center_id, id),
    -- A retried request (double tap, flaky network) never records the payment twice.
    constraint uq_payment_idempotency unique (center_id, idempotency_key),
    -- Reserved for online providers (Payme/Click) later.
    constraint uq_payment_provider_txn unique (provider, provider_txn_id),
    constraint chk_payment_amount check (amount > 0),
    constraint chk_payment_method check (method in ('CASH', 'CARD', 'TRANSFER', 'OTHER')),
    constraint chk_payment_status check (status in ('ACTIVE', 'VOIDED')),
    constraint chk_payment_void check ((status = 'VOIDED') = (voided_at is not null))
);

create index ix_payment_student on payment (center_id, student_id, paid_at);
create index ix_payment_center_paid on payment (center_id, paid_at) where status = 'ACTIVE';

create table payment_allocation
(
    id         uuid primary key,
    center_id  uuid           not null,
    payment_id uuid           not null,
    charge_id  uuid           not null,
    amount     numeric(15, 2) not null,
    created_at timestamptz    not null,
    constraint fk_alloc_payment foreign key (center_id, payment_id) references payment (center_id, id),
    constraint fk_alloc_charge foreign key (center_id, charge_id) references tuition_charge (center_id, id),
    constraint uq_alloc_payment_charge unique (payment_id, charge_id),
    constraint chk_alloc_amount check (amount > 0)
);

create index ix_alloc_charge on payment_allocation (charge_id);

create table audit_log
(
    id            uuid primary key,
    center_id     uuid references education_center (id),
    actor_user_id uuid references app_user (id),
    action        varchar(64) not null,
    entity_type   varchar(32) not null,
    entity_id     uuid,
    data          jsonb,
    created_at    timestamptz not null
);

create index ix_audit_center_created on audit_log (center_id, created_at desc);
