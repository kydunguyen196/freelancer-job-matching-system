create table if not exists proposals (
    id bigserial primary key,
    job_id bigint not null,
    client_id bigint,
    freelancer_id bigint not null,
    freelancer_email varchar(255) not null,
    cover_letter varchar(4000) not null,
    price numeric(12, 2) not null,
    duration_days integer not null,
    status varchar(32) not null,
    reviewed_by_client_id bigint,
    reviewed_at timestamptz,
    rejected_by_client_id bigint,
    rejected_at timestamptz,
    feedback_message varchar(2000),
    interview_scheduled_at timestamptz,
    interview_ends_at timestamptz,
    interview_meeting_link varchar(512),
    interview_notes varchar(2000),
    google_event_id varchar(255),
    accepted_by_client_id bigint,
    accepted_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_proposals_job_freelancer unique (job_id, freelancer_id)
);

create table if not exists proposal_cv_files (
    id bigserial primary key,
    proposal_id bigint not null,
    owner_user_id bigint not null,
    object_key varchar(512) not null,
    original_file_name varchar(255) not null,
    content_type varchar(255) not null,
    size_bytes bigint not null,
    storage_provider varchar(32) not null,
    bucket_name varchar(255),
    uploaded_at timestamptz not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_proposal_cv_files_proposal unique (proposal_id)
);

alter table proposal_cv_files
    add constraint fk_proposal_cv_files_proposal
    foreign key (proposal_id) references proposals (id) on delete cascade;

create index if not exists idx_proposals_client_id on proposals (client_id);
create index if not exists idx_proposals_freelancer_id on proposals (freelancer_id);
create index if not exists idx_proposals_job_status on proposals (job_id, status);
create index if not exists idx_proposal_cv_files_owner on proposal_cv_files (owner_user_id);
