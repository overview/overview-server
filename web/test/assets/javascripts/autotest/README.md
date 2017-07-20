Autotest
========

Helps run JavaScript tests

1. Install [NodeJS](http://nodejs.org)
2. `cd` to this directory
3. Run `npm install`
4. Run `npm test` or `npm test-continuously`

`npm test` will run the tests once and give a status code of `0` if they all
pass. `npm test-continuously` will stay open and re-run tests every time a
JavaScript or CoffeeScript file changes.

Editing the Framework
=====================

1. Turn off the running `npm` process
2. Edit files in `../framework/`
3. Edit `./karma.conf.coffee`
4. Start `npm` again
