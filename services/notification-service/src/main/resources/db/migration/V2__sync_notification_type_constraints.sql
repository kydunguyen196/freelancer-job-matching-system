do $$
declare
    allowed_values text[];
    quoted_values text;
begin
    select array_agg(distinct value order by value)
    into allowed_values
    from (
        select unnest(array[
            'PROPOSAL_CREATED',
            'PROPOSAL_ACCEPTED',
            'PROPOSAL_REJECTED',
            'APPLICATION_FEEDBACK',
            'INTERVIEW_SCHEDULED',
            'JOB_PUBLISHED',
            'JOB_STATUS_CHANGED',
            'MILESTONE_COMPLETED'
        ]) as value
        union
        select type as value
        from notifications
        where type is not null
        union
        select notification_type as value
        from email_delivery_tasks
        where notification_type is not null
    ) values_union;

    select string_agg(quote_literal(value), ',')
    into quoted_values
    from unnest(allowed_values) as value;

    execute 'alter table notifications drop constraint if exists notifications_type_check';
    execute 'alter table notifications add constraint notifications_type_check check (type in (' || quoted_values || '))';

    execute 'alter table email_delivery_tasks drop constraint if exists email_delivery_tasks_notification_type_check';
    execute 'alter table email_delivery_tasks add constraint email_delivery_tasks_notification_type_check check (notification_type in (' || quoted_values || '))';
end
$$;
