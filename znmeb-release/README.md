# Building a Release Zipfile

1. Dependencies:
    * On CompJournoStick 21, all the dependencies will be there.
    * On Fedora 20 and later, you will need
        * java-1.7.0-openjdk-devel or later
        * postgresql-9.3 or later
        * gcc
        * make
        * git
        * libreoffice
    * On Ubuntu 14.04 LTS "Trusty Tahr" you will need
        * openjdk-7-jdk or later
        * postgresql-9.3 or later
        * build-essential
        * git
        * libreoffice
        * zip
        * unzip
    * On Windows or MacOS X I've built Docker images to perform these operations. See https://github.com/znmeb/overview-docker for the details.

1. ***VERY IMPORTANT:*** You will need to edit `/etc/hosts`. Open a terminal and type

    ```
    hostname
    ```
    For example, on my workstation, the hostname is 'AlgoCompSynth'.

    Edit `/etc/hosts` and ***add*** the hostname after 'localhost' on the line for `127.0.0.1`. On my workstation, it's now

    ```
    127.0.0.1  localhost.localdomain localhost AlgoCompSynth
    ```

1. Clone this repository.

1. Open a terminal and type

    ```
    cd overview-server/znmeb-docker
    ./sync.bash
    cd ..
    ./dev
    ```

    `./sync.bash` will sync the source to the Overview project master. `./dev` will start a developer build. The build takes a long time. When it's done it will start a local server and open a browser window / tab to localhost:9000. After a few more compiles you will see the Overview login page. Press <kbd>ctrl</kbd>+<kbd>C</kbd> to stop the server. Then close the browser window / tab.

1. Type

    ```
    auto/clean-fully.sh
    ./build overview-server.zip
    ```
    
    This will also take some time. When it's finished you will have a file `overview-server.zip` ready for deployment.

1. To test the release, move `overview-server.zip` to a directory where you can write and unpack it. Then type

    ```
    cd overview-server
    ./run
    ```

    As with the developer build, it will start a server on localhost:9000 and open a browser window / tab to the server. However, in this case it comes up in 'Example document sets' rather than in the Overview login page. Once the browser window / tab displays, you can close it and stop the server with <kbd>ctrl</kbd>+<kbd>C</kbd>.
