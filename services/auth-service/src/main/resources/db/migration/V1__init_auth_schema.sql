create table if not exists auth_users (
    id bigserial primary key,
    email varchar(255) not null,
    password_hash varchar(255) not null,
    role varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    constraint uk_auth_users_email unique (email)
);

create index if not exists idx_auth_users_role on auth_users (role);
