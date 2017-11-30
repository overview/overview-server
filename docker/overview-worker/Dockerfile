FROM overview-java:build

COPY run-worker /opt/overview/run-worker

# Port 9030: akka-remote from overview-web
EXPOSE 9030

CMD [ "./run-worker" ]
