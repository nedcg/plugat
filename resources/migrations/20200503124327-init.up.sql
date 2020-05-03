CREATE TABLE plugs (
    id char(24),
    title varchar(255),
    description varchar(1024),
    type varchar(24) not null,
    latitude decimal(10,6) not null,
    longitude decimal(10,6) not null,
    created_by_id char(36) not null,
    created_by_name varchar(120) not null,
    created_at timestamp default current_timestamp,
    updated_at timestamp default current_timestamp,
    primary key (id)
);
--;;
CREATE TABLE messages (
    id char(24),
    payload varchar(1024) not null,
    plug_id char(24) not null,
    reply_to char(24),
    created_by_id char(36) not null,
    created_by_name varchar(120) not null,
    primary key (id),
    constraint fk_plug_1 foreign key (plug_id) references plugs(id),
    constraint fk_message_1 foreign key (reply_to) references messages(id)
);
--;;
CREATE TABLE user_plug (
    user_id char(36),
    plug_id char(24),
    primary key (user_id, plug_id),
    constraint fk_plug_2 foreign key (plug_id) references plugs(id)
);