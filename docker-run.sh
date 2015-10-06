#!/bin/bash


# Start support services
docker run --name overview-database -v /opt/overview-data/postgres:/var/lib/postgresql/data -d overview/database
docker run --name overview-redis -d redis:2.8
docker run --name overview-searchindex \
  -v /opt/overview-data/elasticsearch:/usr/share/elasticsearch/data \
  -d elasticsearch:1.7 elasticsearch \
  -Des.cluster.name="DevSearchIndex" \
  -Des.node.name="SearchIndex" \
  -Des.index.number_of_shards=1 



# Wait for evolutions to be applied before starting other services
docker run --name db-evolution-applier \
  --link overview-database \
  --rm overview/db-evolution-applier


# Start overview services

docker run --name documentset-worker \
  --link overview-database \
  --link overview-messagebroker \
  --link overview-searchindex \
  -v /opt/overview-data/blob-storage:/var/lib/overview/blob-storage \
  -d overview/documentset-worker

docker run --name worker \
  --link overview-database \
  --link overview-searchindex \
  -v /opt/overview-data/blob-storage:/var/lib/overview/blob-storage \
  -d overview/worker

docker run --name web \
  --link overview-database \
  --link overview-messagebroker \
  --link overview-searchindex \
  --link overview-redis \
  -v /opt/overview-data/blob-storage:/var/lib/overview/blob-storage \
  -p 9000:9000 \
  -d overview/web
