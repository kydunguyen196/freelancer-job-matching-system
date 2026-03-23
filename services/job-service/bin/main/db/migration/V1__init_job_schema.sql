create table if not exists jobs (
    id bigserial primary key,
    title varchar(150) not null,
    description varchar(4000) not null,
    company_name varchar(255),
    location varchar(255),
    budget_min numeric(12, 2) not null,
    budget_max numeric(12, 2) not null,
    status varchar(32) not null,
    employment_type varchar(32) not null,
    remote boolean not null,
    experience_years integer,
    client_id bigint not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    published_at timestamptz,
    expires_at timestamptz,
    closed_at timestamptz
);

create table if not exists job_tags (
    job_id bigint not null,
    tag varchar(50) not null,
    constraint uk_job_tags_job_tag unique (job_id, tag)
);

alter table job_tags
    add constraint fk_job_tags_job
    foreign key (job_id) references jobs (id) on delete cascade;

create table if not exists saved_jobs (
    id bigserial primary key,
    user_id bigint not null,
    job_id bigint not null,
    job_owner_client_id bigint not null,
    created_at timestamptz not null,
    constraint uk_saved_jobs_user_job unique (user_id, job_id)
);

create table if not exists followed_companies (
    id bigserial primary key,
    follower_user_id bigint not null,
    client_id bigint not null,
    company_name varchar(255),
    created_at timestamptz not null,
    constraint uk_followed_companies_user_client unique (follower_user_id, client_id)
);

create index if not exists idx_jobs_client_id on jobs (client_id);
create index if not exists idx_jobs_status_created_at on jobs (status, created_at desc);
create index if not exists idx_jobs_company_name on jobs (company_name);
create index if not exists idx_jobs_location on jobs (location);
create index if not exists idx_job_tags_job_id on job_tags (job_id);
create index if not exists idx_saved_jobs_user_id on saved_jobs (user_id);
create index if not exists idx_saved_jobs_job_id on saved_jobs (job_id);
create index if not exists idx_followed_companies_follower on followed_companies (follower_user_id);
create index if not exists idx_followed_companies_client on followed_companies (client_id);
