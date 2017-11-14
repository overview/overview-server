FROM openjdk:8u151-jdk

# Deps:
# tesseract+libreoffice: for worker
# nodejs+build-essential+xvfb+libxss1+libgconf-2-4: for web/js tests (which run Electron and Node)
# zip+unzip: for ./build
RUN true \
      && curl -sL https://deb.nodesource.com/setup_8.x | bash \
      && apt-get update \
      && apt-get -y install \
        libreoffice \
        tesseract-ocr $(bash -c 'echo tesseract-ocr-{osd,ara,cat,deu,eng,fra,ita,nld,nor,por,rus,spa,swe}') \
        nodejs build-essential xvfb libxss1 libgconf-2-4 \
        zip \
      && touch /this-is-overview-dev-on-docker

WORKDIR /app

EXPOSE 80

CMD ./sbt all/compile db-evolution-applier/run test-db-evolution-applier/run worker/re-start 'web/run 80'
