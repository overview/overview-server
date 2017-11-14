FROM openjdk:8u151-jdk

RUN true \
      && curl -sL https://deb.nodesource.com/setup_8.x | bash \
      && apt-get update \
      && apt-get -y install tesseract-ocr-all libreoffice build-essential nodejs \
      && touch /this-is-overview-dev-on-docker

WORKDIR /app

EXPOSE 80

CMD ./sbt all/compile db-evolution-applier/run test-db-evolution-applier/run worker/re-start 'web/run 80'
