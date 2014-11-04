FROM znmeb/overview-release
MAINTAINER M. Edward (Ed) Borasky "znmeb@znmeb.net"

USER overview
WORKDIR /home/overview/overview-server-source/
EXPOSE 9000
CMD [ "./dev" ]

# timestamp
RUN echo `date -u +%Y%m%dt%H%M%Sz` > /home/overview/developer-timestamp.txt
