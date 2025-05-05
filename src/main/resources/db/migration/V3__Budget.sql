create table author
(
    id              serial primary key,
    full_name       text not null,
    creation_date   timestamp default current_timestamp
);
