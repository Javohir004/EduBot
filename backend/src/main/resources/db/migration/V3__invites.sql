-- One-time invitations that link a Telegram account to a center role.
-- Only the SHA-256 hash of the token is stored; the raw token exists only in the invite link.
create table invite
(
    id             uuid primary key,
    center_id      uuid        not null references education_center (id),
    role           varchar(16) not null,
    expected_phone varchar(16) not null,
    token_hash     varchar(64) not null,
    expires_at     timestamptz not null,
    used_at        timestamptz,
    used_by        uuid references app_user (id),
    revoked_at     timestamptz,
    created_by     uuid references app_user (id),
    created_at     timestamptz not null,
    constraint uq_invite_token_hash unique (token_hash),
    constraint uq_invite_center_id unique (center_id, id),
    constraint chk_invite_role check (role in ('OWNER', 'ADMIN', 'TEACHER', 'PARENT')),
    constraint chk_invite_used check ((used_at is null) = (used_by is null)),
    constraint chk_invite_final_state check (used_at is null or revoked_at is null)
);

create index ix_invite_center_created on invite (center_id, created_at desc);
