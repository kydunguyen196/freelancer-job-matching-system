alter table jobs
    add column if not exists requirements varchar(6000),
    add column if not exists responsibilities varchar(6000),
    add column if not exists benefits varchar(4000),
    add column if not exists work_mode varchar(16),
    add column if not exists visibility varchar(16),
    add column if not exists category varchar(120),
    add column if not exists openings integer;

update jobs
set work_mode = case when remote then 'REMOTE' else 'ONSITE' end
where work_mode is null;

update jobs
set visibility = 'PUBLIC'
where visibility is null;

alter table jobs
    alter column work_mode set default 'ONSITE',
    alter column visibility set default 'PUBLIC',
    alter column work_mode set not null,
    alter column visibility set not null;

create index if not exists idx_jobs_work_mode on jobs (work_mode);
create index if not exists idx_jobs_visibility on jobs (visibility);
create index if not exists idx_jobs_category on jobs (category);
create index if not exists idx_jobs_client_created_at on jobs (client_id, created_at desc);
