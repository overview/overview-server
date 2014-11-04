# Building the Overview Docker Images Locally

This should run on any recent 64-bit Linux, but I've only tested with Fedora 21 Alpha.

1. Install Docker. On Fedora, install the package `docker-io`.
1. Edit `/etc/group` and add yourself to the `docker` group.
1. Do `sudo systemctl start docker`.
1. Log out and in again so you're recognized as in the `docker` group.
1. Open a terminal and type `./run-all.bash`.

The first time this runs it will take a fair amount of time, but once Docker has accumulated a cache of intermediate image pieces it will go much faster. There are two places where the activity will pause:

1. During the `release` build, you will see the console stop moving. Here's a screenshot:

![Redis Ready](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/Dockerfiles/RedisReady.png)

Although it says it's accepting connections on port 9020, you'll need to open a browser window / tab to `localhost:9000` and wait until the Overview document set window displays. The console will look like this:

![Application Ready](https://raw.githubusercontent.com/znmeb/overview-server/master/znmeb-release/Dockerfiles/ApplicationReady.png)

Go back to the console and press <kbd>ctrl</kbd>+<kbd>D</kbd> and execution will continue. Ignore any error messages.

1. During the `developer` build the console will stop moving. When th
is happens, open a browser window / tab to localhost:9000 and wait until the Ove
rview document set window displays. Then press <kbd>ctrl</kbd>+<kbd>D</kbd> and
execution will continue. Ignore any error messages.

