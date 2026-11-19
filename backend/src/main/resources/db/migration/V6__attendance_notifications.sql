-- Lessons (one per group slot and date), attendance marks and the notification outbox.

create table lesson
(
    id          uuid primary key,
    center_id   uuid        not null,
    group_id    uuid        not null,
    lesson_date date        not null,
    start_time  time        not null,
    status      varchar(16) not null default 'HELD',
    created_by  uuid references app_user (id),
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint uq_lesson_center_id unique (center_id, id),
    constraint fk_lesson_group foreign key (center_id, group_id) references study_group (center_id, id),
    constraint uq_lesson_slot unique (group_id, lesson_date, start_time),
    constraint chk_lesson_status check (status in ('HELD', 'CANCELLED'))
);

create index ix_lesson_group_date on lesson (center_id, group_id, lesson_date);

create table attendance
(
    id         uuid primary key,
    center_id  uuid        not null,
    lesson_id  uuid        not null,
    student_id uuid        not null,
    status     varchar(16) not null,
    marked_by  uuid        not null references app_user (id),
    marked_at  timestamptz not null,
    constraint fk_attendance_lesson foreign key (center_id, lesson_id) references lesson (center_id, id),
    constraint fk_attendance_student foreign key (center_id, student_id) references student (center_id, id),
    -- One mark per student per lesson; re-marking updates the row (upsert).
    constraint uq_attendance_lesson_student unique (lesson_id, student_id),
    constraint chk_attendance_status check (status in ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED'))
);

create index ix_attendance_student on attendance (center_id, student_id);

-- Outbox: rows are written in the business transaction and sent by a scheduled dispatcher.
create table notification
(
    id                uuid primary key,
    center_id         uuid references education_center (id),
    recipient_user_id uuid         not null references app_user (id),
    type              varchar(32)  not null,
    payload           jsonb        not null,
    dedup_key         varchar(200) not null,
    status            varchar(16)  not null,
    attempts          int          not null default 0,
    scheduled_at      timestamptz  not null,
    sent_at           timestamptz,
    last_error        varchar(500),
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    constraint uq_notification_dedup unique (dedup_key),
    constraint chk_notification_status check (status in ('PENDING', 'SENT', 'FAILED', 'CANCELLED'))
);

create index ix_notification_due on notification (scheduled_at) where status = 'PENDING';

create table shedlock
(
    name       varchar(64) primary key,
    lock_until timestamptz  not null,
    locked_at  timestamptz  not null,
    locked_by  varchar(255) not null
);
