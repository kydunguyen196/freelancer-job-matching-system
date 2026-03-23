alter table user_profiles
    add column if not exists avatar_file_name varchar(255),
    add column if not exists avatar_content_type varchar(120),
    add column if not exists avatar_data bytea,
    add column if not exists avatar_uploaded_at timestamptz,
    add column if not exists company_logo_file_name varchar(255),
    add column if not exists company_logo_content_type varchar(120),
    add column if not exists company_logo_data bytea,
    add column if not exists company_logo_uploaded_at timestamptz;
