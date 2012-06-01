# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table document (
  id                        bigint not null,
  document_cloud_id         varchar(255),
  title                     varchar(255),
  canonical_url             varchar(255),
  constraint pk_document primary key (id))
;

create table document_reference (
  id                        bigint not null,
  document_set_id_id        bigint,
  document_id               bigint,
  constraint pk_document_reference primary key (id))
;

create table document_set (
  id                        bigint not null,
  query                     varchar(255),
  constraint pk_document_set primary key (id))
;

create sequence document_seq;

create sequence document_reference_seq;

create sequence document_set_seq;

alter table document_reference add constraint fk_document_reference_document_1 foreign key (document_set_id_id) references document_set (id) on delete restrict on update restrict;
create index ix_document_reference_document_1 on document_reference (document_set_id_id);



# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists document;

drop table if exists document_reference;

drop table if exists document_set;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists document_seq;

drop sequence if exists document_reference_seq;

drop sequence if exists document_set_seq;

