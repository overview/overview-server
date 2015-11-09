FROM overview/overview-base

ENV \
  DATABASE_NAME=overview \
  DATABASE_PORT=5432 \
  ES_CLUSTER_NAME=overview \
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
