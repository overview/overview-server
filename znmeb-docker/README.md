# Building a release on Fedora 21 / CompJournoStick

1. Clone this repository.
1. Open a terminal and type

    ```
    cd overview-server/znmeb-docker
    ./sync.bash
    cd ..
    ./dev
    ```

    `sync.bash` will sync the source to the Overview project master. `./dev` will start a developer build. The build takes a long time. When it's done it will start a local server and open a browser to localhost:9000. After a few more compiles you will see the Overview login page. Press <kbd>ctrl</kbd>+<kbd>C</kbd> to stop the server.
