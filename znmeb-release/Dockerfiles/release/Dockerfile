FROM znmeb/overview-release-template
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

USER root
WORKDIR /home/overview/
COPY overview-release.zip .
RUN unzip overview-release.zip
RUN chown -R overview:overview /home/overview/

# run time
USER overview
WORKDIR /home/overview/overview-release
EXPOSE 9000
CMD [ "/usr/bin/time ./run" ]
