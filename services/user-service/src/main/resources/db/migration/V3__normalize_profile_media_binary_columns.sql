do $$
declare
    media_column text;
    column_type text;
    temp_column text;
begin
    foreach media_column in array array['resume_data', 'avatar_data', 'company_logo_data']
    loop
        select c.udt_name
        into column_type
        from information_schema.columns c
        where c.table_schema = 'public'
          and c.table_name = 'user_profiles'
          and c.column_name = media_column;

        if column_type is null then
            execute format('alter table user_profiles add column %I bytea', media_column);
        elsif column_type = 'oid' then
            temp_column := media_column || '__tmp_bytea';

            execute format('alter table user_profiles add column %I bytea', temp_column);
            execute format(
                'update user_profiles set %1$I = case ' ||
                'when %2$I is null then null ' ||
                'when exists (select 1 from pg_largeobject_metadata where oid = %2$I) then lo_get(%2$I) ' ||
                'else null end',
                temp_column,
                media_column
            );
            execute format('alter table user_profiles drop column %I', media_column);
            execute format('alter table user_profiles rename column %I to %I', temp_column, media_column);
        end if;
    end loop;
end
$$;
