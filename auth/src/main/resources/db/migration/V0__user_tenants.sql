create table if not exists users
(
    username text    not null primary key,
    password text    not null,
    enabled  boolean not null
);

create table if not exists authorities
(
    username  text not null references users (username),
    authority text not null
    );

create unique index if not exists index_auth_username ON authorities USING btree (username, authority);

create table if not exists tenant_details
(
    id         serial primary key,
    identifier text not null unique default pg_catalog.gen_random_uuid(),
    enabled    boolean              default true,
    created_at timestamp            default now()
    );

create table if not exists tenant_details_attributes
(
    tenant_id       integer not null references tenant_details (id),
    attribute_name  text    not null,
    attribute_value text    not null,
    primary key (tenant_id, attribute_name)
    );

create table if not exists users_tenant_details
(
    users_username            text not null references users (username),
    tenant_details_identifier text not null references tenant_details (identifier),
    primary key (users_username, tenant_details_identifier)
);