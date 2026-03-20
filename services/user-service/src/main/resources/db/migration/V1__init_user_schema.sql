create table if not exists user_profiles (
    id bigserial primary key,
    auth_user_id bigint not null,
    email varchar(255) not null,
    role varchar(32) not null,
    hourly_rate numeric(12, 2),
    overview varchar(4000),
    company_name varchar(255),
    contact_email varchar(255),
    phone_number varchar(32),
    address varchar(255),
    company_address varchar(255),
    resume_file_name varchar(255),
    resume_content_type varchar(120),
    resume_data bytea,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_user_profiles_auth_user_id unique (auth_user_id)
);

create table if not exists user_profile_skills (
    profile_id bigint not null,
    skill varchar(80) not null
);

alter table user_profile_skills
    add constraint fk_user_profile_skills_profile
    foreign key (profile_id) references user_profiles (id) on delete cascade;

create index if not exists idx_user_profiles_email on user_profiles (email);
create index if not exists idx_user_profiles_role on user_profiles (role);
create index if not exists idx_user_profile_skills_profile on user_profile_skills (profile_id);
