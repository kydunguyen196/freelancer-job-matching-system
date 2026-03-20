create table if not exists contracts (
    id bigserial primary key,
    source_proposal_id bigint not null,
    job_id bigint not null,
    client_id bigint not null,
    freelancer_id bigint not null,
    status varchar(32) not null,
    created_at timestamptz not null,
    updated_at timestamptz not null,
    completed_at timestamptz,
    constraint uk_contracts_source_proposal_id unique (source_proposal_id)
);

create table if not exists milestones (
    id bigserial primary key,
    contract_id bigint not null,
    title varchar(255) not null,
    amount numeric(12, 2) not null,
    due_date date not null,
    status varchar(32) not null,
    completed_at timestamptz,
    created_at timestamptz not null,
    updated_at timestamptz not null
);

alter table milestones
    add constraint fk_milestones_contract
    foreign key (contract_id) references contracts (id) on delete cascade;

create index if not exists idx_contracts_client_id on contracts (client_id);
create index if not exists idx_contracts_freelancer_id on contracts (freelancer_id);
create index if not exists idx_milestones_contract_id on milestones (contract_id);
