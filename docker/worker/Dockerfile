FROM overview/overview-base


ENV DATABASE_NAME=overview \
    DATABASE_PASSWORD=overview \
    DATABASE_SERVER_NAME=overview-database \
    DATABASE_PORT=5432 \
    ES_HOSTS="overview-searchindex:9300" \
    ES_CLUSTER_NAME="DevSearchIndex"

RUN mkdir /opt/overview/bin && \
  mv worker/target/universal/stage /opt/overview/bin && \
  cd /opt/overview && \
  rm -rf src

WORKDIR /opt/overview/bin

CMD ["stage/bin/worker"]

