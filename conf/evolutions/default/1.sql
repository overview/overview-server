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

create table document_set (
  id                        bigint not null,
  query                     varchar(255),
  constraint pk_document_set primary key (id))
;

create sequence document_seq;

create sequence document_set_seq;




# --- !Downs

SET REFERENTIAL_INTEGRITY FALSE;

drop table if exists document;

drop table if exists document_set;

SET REFERENTIAL_INTEGRITY TRUE;

drop sequence if exists document_seq;

drop sequence if exists document_set_seq;

