FROM openjdk:10.0.1-10-jdk-sid

# Deps:
# nodejs+build-essential+xvfb+libxss1+libgconf-2-4: for web/js tests (which run Electron and Node)
# zip+unzip: for ./build
RUN true \
      && curl -sL https://deb.nodesource.com/setup_10.x | bash \
      && apt-get update \
      && apt-get -y install \
        nodejs \
        build-essential \
        xvfb \
        libxss1 \
        libgconf-2-4 \
        zip \
      && touch /this-is-overview-dev-on-docker

RUN mkdir -p /opt/overview

WORKDIR /app

EXPOSE 80

CMD [ "/app/sbt", "all/compile", "db-evolution-applier/run", "test-db-evolution-applier/run", "worker/reStart", "web/run 80" ]
