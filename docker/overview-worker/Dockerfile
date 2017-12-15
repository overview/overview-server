# TODO don't use :latest
FROM overview/pdfocr-native:latest as pdfocr

FROM overview-java:build

COPY --from=pdfocr /split-pdf-and-extract-text /opt/overview/
COPY run-worker /opt/overview/run-worker

# Port 9030: akka-remote from overview-web
EXPOSE 9030

CMD [ "./run-worker" ]
