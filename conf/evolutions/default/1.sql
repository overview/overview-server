# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table document (
  id                        bigint not null,
  title                     varchar(255),
  text_url                  varchar(255),
  view_url                  varchar(255),
  document_set_id           bigint,
  constraint pk_document primary key (id))
;

create table document_set (
  id                        bigint not null,
  query                     varchar(255),
  constraint pk_document_set primary key (id))
;

create table document_set_creation_job (
  id                        bigint not null,
  query                     varchar(255),
  state                     integer,
  constraint ck_document_set_creation_job_state check (state in (0,1,2)),
  constraint pk_document_set_creation_job primary key (id))
;

create table node (
  id                        bigint not null,
  description               varchar(255),
  parent_id                 bigint,
  constraint pk_node primary key (id))
;

create table tag (
  id                        bigint not null,
  name                      varchar(255),
  document_set_id           bigint,
  constraint uq_tag_1 unique (document_set_id,name),
  constraint pk_tag primary key (id))
;

create table tree (
  id                        bigint not null,
  root_id                   bigint,
  constraint pk_tree primary key (id))
;


create table document_tag (
  document_id                    bigint not null,
  tag_id                         bigint not null,
  constraint pk_document_tag primary key (document_id, tag_id))
;

create table node_document (
  node_id                        bigint not null,
  document_id                    bigint not null,
  constraint pk_node_document primary key (node_id, document_id))
;
create sequence document_seq;

create sequence document_set_seq;

create sequence document_set_creation_job_seq;

create sequence node_seq;

create sequence tag_seq;

create sequence tree_seq;

alter table document add constraint fk_document_documentSet_1 foreign key (document_set_id) references document_set (id);
create index ix_document_documentSet_1 on document (document_set_id);
alter table node add constraint fk_node_parent_2 foreign key (parent_id) references node (id);
create index ix_node_parent_2 on node (parent_id);
alter table tag add constraint fk_tag_documentSet_3 foreign key (document_set_id) references document_set (id);
create index ix_tag_documentSet_3 on tag (document_set_id);
alter table tree add constraint fk_tree_root_4 foreign key (root_id) references node (id);
create index ix_tree_root_4 on tree (root_id);



alter table document_tag add constraint fk_document_tag_document_01 foreign key (document_id) references document (id);

alter table document_tag add constraint fk_document_tag_tag_02 foreign key (tag_id) references tag (id);

alter table node_document add constraint fk_node_document_node_01 foreign key (node_id) references node (id);

alter table node_document add constraint fk_node_document_document_02 foreign key (document_id) references document (id);

# --- !Downs

drop table if exists document cascade;

drop table if exists document_tag cascade;

drop table if exists node_document cascade;

drop table if exists document_set cascade;

drop table if exists document_set_creation_job cascade;

drop table if exists node cascade;

drop table if exists tag cascade;

drop table if exists tree cascade;

drop sequence if exists document_seq;

drop sequence if exists document_set_seq;

drop sequence if exists document_set_creation_job_seq;

drop sequence if exists node_seq;

drop sequence if exists tag_seq;

drop sequence if exists tree_seq;

