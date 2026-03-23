create table if not exists notifications (
    id bigserial primary key,
    recipient_user_id bigint not null,
    type varchar(64) not null,
    title varchar(255) not null,
    message varchar(1000) not null,
    read boolean not null default false,
    read_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create table if not exists email_delivery_tasks (
    id bigserial primary key,
    notification_type varchar(64) not null,
    recipient_email varchar(255) not null,
    subject varchar(255) not null,
    body varchar(4000) not null,
    status varchar(32) not null,
    attempt_count integer not null,
    max_attempts integer not null,
    next_attempt_at timestamptz not null,
    last_error varchar(1000),
    sent_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_notifications_recipient_created on notifications (recipient_user_id, created_at desc);
create index if not exists idx_notifications_recipient_read on notifications (recipient_user_id, read);
create index if not exists idx_email_delivery_tasks_status_next_attempt on email_delivery_tasks (status, next_attempt_at);
