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



RUN \
  export FILENAMES="$(unzip -qq -p production.zip archive/frontend/classpath.txt | \
    sed -e 's/^/archive\/lib\//')" && \
  unzip -qq -d bin -j production.zip $FILENAMES && \
  rm production.zip

WORKDIR /opt/overview

CMD ["/usr/bin/java", \
       "-cp", "bin/*", \
        "-Dfile.encoding=UTF8", \
        "-Duser.timezone=UTC", \
        "-Dpidfile.path=/dev/null", \
        "play.core.server.ProdServerStart"]





