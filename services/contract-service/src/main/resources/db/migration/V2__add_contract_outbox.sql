create table if not exists contract_outbox_events (
    id bigserial primary key,
    aggregate_type varchar(64) not null,
    aggregate_id bigint,
    event_type varchar(64) not null,
    exchange_name varchar(255) not null,
    routing_key varchar(255) not null,
    payload text not null,
    attempts integer not null default 0,
    next_attempt_at timestamptz not null,
    published_at timestamptz,
    last_error varchar(2000),
    created_at timestamptz not null,
    updated_at timestamptz not null
);

create index if not exists idx_contract_outbox_dispatch
    on contract_outbox_events (published_at, next_attempt_at, created_at);
