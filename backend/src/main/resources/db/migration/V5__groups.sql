-- Study groups, weekly schedules and enrollments (student membership in a group over time).

create table study_group
(
    id          uuid primary key,
    center_id   uuid           not null references education_center (id),
    teacher_id  uuid,
    name        varchar(120)   not null,
    subject     varchar(120),
    monthly_fee numeric(15, 2) not null,
    start_date  date           not null,
    end_date    date,
    capacity    int,
    status      varchar(16)    not null,
    version     bigint         not null default 0,
    created_at  timestamptz    not null,
    updated_at  timestamptz    not null,
    constraint uq_group_center_id unique (center_id, id),
    constraint fk_group_teacher foreign key (center_id, teacher_id) references teacher (center_id, id),
    constraint chk_group_fee check (monthly_fee >= 0),
    constraint chk_group_dates check (end_date is null or end_date >= start_date),
    constraint chk_group_capacity check (capacity is null or capacity > 0),
    constraint chk_group_status check (status in ('ACTIVE', 'COMPLETED'))
);

create index ix_group_center_status on study_group (center_id, status, name);
create index ix_group_teacher on study_group (center_id, teacher_id);

create table group_schedule
(
    id          uuid primary key,
    center_id   uuid     not null,
    group_id    uuid     not null,
    day_of_week smallint not null,
    start_time  time     not null,
    end_time    time     not null,
    room        varchar(60),
    constraint fk_schedule_group foreign key (center_id, group_id) references study_group (center_id, id),
    constraint uq_schedule_slot unique (group_id, day_of_week, start_time),
    constraint chk_schedule_day check (day_of_week between 1 and 7),
    constraint chk_schedule_time check (end_time > start_time)
);

create table enrollment
(
    id           uuid primary key,
    center_id    uuid        not null,
    student_id   uuid        not null,
    group_id     uuid        not null,
    joined_on    date        not null,
    left_on      date,
    status       varchar(16) not null,
    fee_override numeric(15, 2),
    end_reason   varchar(300),
    version      bigint      not null default 0,
    created_at   timestamptz not null,
    updated_at   timestamptz not null,
    constraint uq_enrollment_center_id unique (center_id, id),
    constraint fk_enrollment_student foreign key (center_id, student_id) references student (center_id, id),
    constraint fk_enrollment_group foreign key (center_id, group_id) references study_group (center_id, id),
    constraint chk_enrollment_status check (status in ('ACTIVE', 'ENDED')),
    constraint chk_enrollment_ended check ((status = 'ENDED') = (left_on is not null)),
    constraint chk_enrollment_dates check (left_on is null or left_on >= joined_on),
    constraint chk_enrollment_fee check (fee_override is null or fee_override >= 0)
);

-- A student can be in a group only once at a time; history rows (ENDED) are kept.
create unique index uq_enrollment_active on enrollment (student_id, group_id) where status = 'ACTIVE';
create index ix_enrollment_group on enrollment (center_id, group_id, status);
create index ix_enrollment_student on enrollment (center_id, student_id);
