create table author
(
    id         serial primary key,
    full_name  varchar(255) not null,
    created_at timestamp not null default current_timestamp
);

alter table budget
add column author_id integer references author(id) on delete set null;