do $$
declare
    allowed_values text[];
    quoted_values text;
begin
    select array_agg(distinct value order by value)
    into allowed_values
    from (
        select unnest(array[
            'PENDING',
            'REVIEWING',
            'INTERVIEW_SCHEDULED',
            'ACCEPTED',
            'REJECTED'
        ]) as value
        union
        select status as value
        from proposals
        where status is not null
    ) values_union;

    select string_agg(quote_literal(value), ',')
    into quoted_values
    from unnest(allowed_values) as value;

    execute 'alter table proposals drop constraint if exists proposals_status_check';
    execute 'alter table proposals add constraint proposals_status_check check (status in (' || quoted_values || '))';
end
$$;
