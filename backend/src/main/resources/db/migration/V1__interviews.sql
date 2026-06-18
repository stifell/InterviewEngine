create table interview (
    id              uuid         primary key,
    position        varchar(100) not null,
    status          varchar(20)  not null,
    transcript_json text         not null,
    result_json     text,
    error_message   text,
    created_at      timestamp    not null,
    updated_at      timestamp    not null
);
