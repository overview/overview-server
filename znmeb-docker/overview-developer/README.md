# Developer Workstation Setup

1. Install Ubuntu 14.04.1 LTS "Trusty Tahr" 64-bit in a virtual machine. The machine needs at least 2 GB of RAM and a 40 GB virtual hard drive.
1. Start the virtual machine.
1. Download and unpack the latest "master" zip archive from https://github.com/znmeb/overview-server/archive/master.zip
1. Open a terminal and type
```
cd overview-server/znmeb-docker/overview-developer
./trusty-workstation.bash
```
This will install the dependencies needed to build and test Overview releases and Docker images.
1. Now type
```
cd ../..
./dev
```
This will download and build all the Overview code. When it's done it will open a browser to the Overview install.
1. Assuming the build was successful, close the browser and type `ctl-c` in the terminal to stop the server.
1. Type
```
auto/fully-clean.sh
./build overview-release.zip
```
This will make a release zipfile. When it's finished, move the zipfile to `znmeb-docker/overview-release`.
