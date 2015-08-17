#!/bin/bash


# Start support services
docker run --name database -h overview-database -d overview/database
docker run --name message-broker -h overview-messagebroker -d overview/message-broker
docker run --name redis -h overview-redis -d redis:2.8
docker run --name searchindex -h overview-searchindex \
  -d elasticsearch:1.7 elasticsearch \
  -Des.cluster.name="DevSearchIndex" \
  -Des.node.name="SearchIndex" \
  -Des.index.number_of_shards=1 



# Wait for evolutions to be applied before starting other services
docker run --name db-evolution-applier \
  --link database:overview-database \
  --rm overview/db-evolution-applier


# Start overview services

docker run --name documentset-worker \
  --link database:overview-database \
  --link message-broker:overview-messagebroker \
  --link searchindex:overview-searchindex \
   -d overview/documentset-worker

docker run --name worker \
  --link database:database \
  --link searchindex:overview-searchindex \
  -d overview/worker

docker run --name web \
  --link database:database \
  --link message-broker:overview-messagebroker \
  --link searchindex:overview-searchindex \
  --link redis:overview-redis \
  -p 9000:9000 \
  -d overview/web
