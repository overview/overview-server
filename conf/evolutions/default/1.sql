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

create table tag (
  id                        bigint not null,
  name                      varchar(255),
  document_set_id           bigint,
  constraint uq_tag_1 unique (document_set_id,name),
  constraint pk_tag primary key (id))
;


create table document_tag (
  document_id                    bigint not null,
  tag_id                         bigint not null,
  constraint pk_document_tag primary key (document_id, tag_id))
;
create sequence document_seq;

create sequence document_set_seq;

create sequence tag_seq;

alter table document add constraint fk_document_documentSet_1 foreign key (document_set_id) references document_set (id) on delete restrict on update restrict;
create index ix_document_documentSet_1 on document (document_set_id);
alter table tag add constraint fk_tag_documentSet_2 foreign key (document_set_id) references document_set (id) on delete restrict on update restrict;
create index ix_tag_documentSet_2 on tag (document_set_id);



alter table document_tag add constraint fk_document_tag_document_01 foreign key (document_id) references document (id) on delete restrict on update restrict;

alter table document_tag add constraint fk_document_tag_tag_02 foreign key (tag_id) references tag (id) on delete restrict on update restrict;

# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists document;

drop table if exists document_tag;

drop table if exists document_set;

drop table if exists tag;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists document_seq;

drop sequence if exists document_set_seq;

drop sequence if exists tag_seq;

