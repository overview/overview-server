# TODO don't use :latest
FROM overview/pdfocr-native:latest as pdfocr

FROM openjdk:8u151-jdk

# Deps:
# tesseract+libreoffice: for worker
# daemontools: softlimit, for worker child processes (TODO make them separate containers)
# nodejs+build-essential+xvfb+libxss1+libgconf-2-4: for web/js tests (which run Electron and Node)
# zip+unzip: for ./build
RUN true \
      && curl -sL https://deb.nodesource.com/setup_8.x | bash \
      && apt-get update \
      && apt-get -y install \
        libreoffice \
        tesseract-ocr $(bash -c 'echo tesseract-ocr-{osd,ara,cat,deu,eng,fra,ita,nld,nor,por,rus,spa,swe}') \
        nodejs build-essential xvfb libxss1 libgconf-2-4 \
        daemontools \
        zip \
      && touch /this-is-overview-dev-on-docker

RUN mkdir -p /opt/overview

COPY --from=pdfocr /split-pdf-and-extract-text /opt/overview/

WORKDIR /app

EXPOSE 80

CMD [ "/app/sbt", "all/compile", "db-evolution-applier/run", "test-db-evolution-applier/run", "worker/reStart", "web/run 80" ]
