FROM overview/overview-base

RUN apt-get update && apt-get install -y \
  libreoffice \
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

ENV \
  DATABASE_NAME=overview \
  DATABASE_PORT=5432 \
  ES_CLUSTER_NAME=overview \
  BLOB_STORAGE_FILE_BASE_DIRECTORY=/var/lib/overview/blob-storage

RUN \
  export FILENAMES="$(unzip -qq -p production.zip archive/documentset-worker/classpath.txt | \
    sed -e 's/^/archive\/lib\//')" && \
  unzip -qq -d bin -j production.zip $FILENAMES && \
  rm production.zip

WORKDIR /opt/overview

CMD ["/usr/bin/java", \
       "-cp", "bin/*", \
        "-Dfile.encoding=UTF8", \
        "-Duser.timezone=UTC", \
        "com.overviewdocs.DocumentSetWorker"]



