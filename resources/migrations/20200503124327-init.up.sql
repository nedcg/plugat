CREATE TABLE plugs (
    id char(24),
    type varchar(24) not null,
    latitude decimal(10,6) not null,
    longitude decimal(10,6) not null,
    created_by_id char(36) not null,
    created_by_name varchar(120) not null,
    title varchar(255),
    description varchar(1024),
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
CREATE TABLE plug_subscriptions (
    user_id char(36),
    plug_id char(24),
    primary key (user_id, plug_id),
    constraint fk_plug_2 foreign key (plug_id) references plugs(id)
);
--;;
INSERT INTO plugs VALUES
('5eb600f68399530caebd32ca','PLACE',43.655507,-79.354108,'2b018796-59e8-4d6e-b413-610f1f5eb949','System','170 Bayview Ave','170 Bayview Ave','2020-01-01','2020-01-01'),
('5eb601063fc5843328c4b191','PLACE',43.6542508,-79.3551338,'2b018796-59e8-4d6e-b413-610f1f5eb949','System','Lawrence Harris Square','Lawrence Harris Square','2020-01-01','2020-01-01'),
('5eb6010f4fb7ae1b1b16507b','PLACE',43.6542508,-79.3551338,'2b018796-59e8-4d6e-b413-610f1f5eb949','System','Corktown Common','Corktown Common','2020-01-01','2020-01-01'),
('5eb60117afe17e277b99f7d8','PLACE',43.6539675,-79.3561532,'2b018796-59e8-4d6e-b413-610f1f5eb949','System','Green Storage Toronto','Green Storage Toronto','2020-01-01','2020-01-01'),
('5eb6011edba68af3fdfab5fa','PLACE',43.6539675,-79.3561532,'2b018796-59e8-4d6e-b413-610f1f5eb949','System','Cooper Koo Family YMCA','Cooper Koo Family YMCA','2020-01-01','2020-01-01'),
('5eb60124b4501c0a6ab5e733','PLACE',43.6529075,-79.358658,'2b018796-59e8-4d6e-b413-610f1f5eb949','System','Honda Downtown','Honda Downtown','2020-01-01','2020-01-01');