FROM overview-java:build

COPY run-web /opt/overview/

# Port 80: HTTP from client (or better: SSL-terminating reverse proxy)
# Port 9031: Akka-remote from overview-worker
EXPOSE 80 9031

CMD [ "./run-web" ]
