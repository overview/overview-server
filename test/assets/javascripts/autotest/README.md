Autotest
========

Helps run JavaScript tests

1. Install NodeJS
2. Run `npm install -g grunt-cli`
3. Run `npm install -g coffee-script` (see https://github.com/gruntjs/grunt/pull/767 to see why we can't just depend on it)
4. Run `npm install` in this directory
5. Run `grunt`

You'll see test status (and, if Growl is running, notifications) every time
a JavaScript or CoffeeScript file changes.

Editing the Framework
=====================

1. Turn off the running `grunt` process
2. Edit files in `../framework/`
3. Edit `./karma.conf.js`
4. Start `grunt` again
