-- Teachers, students and guardians. All tenant-owned: composite FKs through center_id make
-- cross-center links impossible at the database level.

create table teacher
(
    id          uuid primary key,
    center_id   uuid         not null references education_center (id),
    user_id     uuid references app_user (id),
    full_name   varchar(200) not null,
    phone       varchar(16),
    subjects    varchar(300),
    status      varchar(16)  not null,
    archived_at timestamptz,
    version     bigint       not null default 0,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint uq_teacher_center_id unique (center_id, id),
    constraint chk_teacher_status check (status in ('ACTIVE', 'ARCHIVED'))
);

create unique index uq_teacher_center_user on teacher (center_id, user_id) where user_id is not null;
create index ix_teacher_center_status on teacher (center_id, status, full_name);

create table student
(
    id          uuid primary key,
    center_id   uuid         not null references education_center (id),
    user_id     uuid references app_user (id),
    first_name  varchar(100) not null,
    last_name   varchar(100) not null,
    birth_date  date,
    phone       varchar(16),
    note        varchar(1000),
    status      varchar(16)  not null,
    archived_at timestamptz,
    version     bigint       not null default 0,
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null,
    constraint uq_student_center_id unique (center_id, id),
    constraint chk_student_status check (status in ('ACTIVE', 'ARCHIVED'))
);

create index ix_student_center_status on student (center_id, status, last_name, first_name);

create table guardian
(
    id         uuid primary key,
    center_id  uuid         not null references education_center (id),
    user_id    uuid references app_user (id),
    full_name  varchar(200) not null,
    phone      varchar(16)  not null,
    created_at timestamptz  not null,
    updated_at timestamptz  not null,
    constraint uq_guardian_center_id unique (center_id, id),
    constraint uq_guardian_center_phone unique (center_id, phone)
);

create table student_guardian
(
    center_id   uuid        not null,
    student_id  uuid        not null,
    guardian_id uuid        not null,
    relation    varchar(16) not null,
    created_at  timestamptz not null,
    primary key (student_id, guardian_id),
    constraint fk_sg_student foreign key (center_id, student_id) references student (center_id, id),
    constraint fk_sg_guardian foreign key (center_id, guardian_id) references guardian (center_id, id),
    constraint chk_sg_relation check (relation in ('MOTHER', 'FATHER', 'GUARDIAN', 'OTHER'))
);

create index ix_sg_guardian on student_guardian (guardian_id);

-- Invites can point at the profile they will be linked to (teacher or guardian).
alter table invite add column target_id uuid;
