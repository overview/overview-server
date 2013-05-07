Autotest
========

Helps run JavaScript tests

1. Install NodeJS
2. sudo npm install -g grunt-cli
3. npm install
4. grunt
5. browse to http://localhost:9876/capture?strict
6. Press Enter when Grunt prompts

How it works
------------

Grunt will do the following:

1. Watch all CoffeeScript files and compile them to Javascript
2. Start a JSTD server
3. Prompt you to browse to http://localhost:9876/capture?strict to connect to the JSTD server
4. Run tests
5. Run tests whenever new JavaScript files are compiled
