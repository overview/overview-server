# Building a release on Fedora 21 / CompJournoStick

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

    `./sync.bash` will sync the source to the Overview project master. `./dev` will start a developer build. The build takes a long time. When it's done it will start a local server and open a browser to localhost:9000. After a few more compiles you will see the Overview login page. Press <kbd>ctrl</kbd>+<kbd>C</kbd> to stop the server. Then close the browser tab.
1. Type

    ```
    auto/clean-fully.sh
    ./build overview-server.zip
    ```
    
    This will also take some time. As with the developer build, when it's done it will start a server on localhost:9000 and open a browser window / tab to the server. In this case it comes up in 'Example document sets' rather than in the login page.
