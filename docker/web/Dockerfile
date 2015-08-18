FROM overview/overview-base



ENV DATABASE_NAME=overview \
    DATABASE_PASSWORD=overview \
    DATABASE_SERVER_NAME=overview-database \
    DATABASE_PORT=5432 \
    MESSAGE_BROKER_URI=tcp://overview-messagebroker:61613 \
    ES_HOSTS="overview-searchindex:9300" \
    ES_CLUSTER_NAME="DevSearchIndex" \
    REDIS_HOST=overview-redis \
    REDIS_PORT=6379 \
    BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage

EXPOSE 9000


RUN mkdir /opt/overview/bin && \
  mv target/universal/stage /opt/overview/bin && \
  cd /opt/overview && \
  rm -rf src

WORKDIR /opt/overview/bin

CMD ["stage/bin/overview-server"]




