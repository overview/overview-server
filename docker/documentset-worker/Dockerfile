FROM overview/overview-base


RUN apt-get install -y \
  libreoffice-common \
  libreoffice-writer \
  libreoffice-calc \
  libreoffice-impress \
  tesseract-ocr \
  tesseract-ocr-ara \
  tesseract-ocr-cat \
  tesseract-ocr-deu \
  tesseract-ocr-fra \
  tesseract-ocr-ita \
  tesseract-ocr-nld \
  tesseract-ocr-por \
  tesseract-ocr-rus \
  tesseract-ocr-spa \
  tesseract-ocr-swe 

ENV DATABASE_NAME=overview \
    DATABASE_PASSWORD=overview \
    DATABASE_SERVER_NAME=overview-database \
    DATABASE_PORT=5432 \
    MESSAGE_BROKER_URI=tcp://overview-messagebroker:61613 \
    ES_HOSTS="overview-searchindex:9300" \
    ES_CLUSTER_NAME="DevSearchIndex" \
    BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage


RUN mkdir /opt/overview/bin && \
  mv documentset-worker/target/universal/stage /opt/overview/bin && \
  cd /opt/overview && \
  rm -rf src


WORKDIR /opt/overview/bin

CMD ["stage/bin/documentset-worker"]

