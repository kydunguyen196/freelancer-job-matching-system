do $$
begin
    if exists (
        select 1
        from information_schema.tables
        where table_schema = 'public'
          and table_name = 'jobs'
    ) then
        if not exists (
            select 1
            from information_schema.columns
            where table_schema = 'public'
              and table_name = 'jobs'
              and column_name = 'remote'
        ) then
            alter table jobs add column remote boolean;
        end if;

        if exists (
            select 1
            from information_schema.columns
            where table_schema = 'public'
              and table_name = 'jobs'
              and column_name = 'work_mode'
        ) then
            update jobs
            set remote = case
                when work_mode = 'REMOTE' then true
                else false
            end
            where remote is null;
        else
            update jobs
            set remote = false
            where remote is null;
        end if;

        alter table jobs alter column remote set default false;
        update jobs
        set remote = false
        where remote is null;
        alter table jobs alter column remote set not null;
    end if;
end
$$;
