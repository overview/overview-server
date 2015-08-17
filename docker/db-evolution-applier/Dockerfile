FROM overview/overview-base

ENV DATABASE_NAME=overview \
    DATABASE_PASSWORD=overview \
    DATABASE_SERVER_NAME=overview-database \
    DATABASE_PORT=5432

RUN mkdir /opt/overview/bin && \
  mv db-evolution-applier/target/db-evolution-applier/universal/stage /opt/overview/bin && \
  cd /opt/overview && \
  rm -rf src

WORKDIR /opt/overview/bin

CMD ["stage/bin/db-evolution-applier"]

