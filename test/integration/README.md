To run the tests:

* npm install # install test-suite dependencies
* npm test    # run tests

Test tags
---------

You can run a subset of tests. For instance, to skip all the tests that won't
run on SauceLabs, run this:

    node_modules/.bin/mocha --grep @SauceLabsKiller --invert

The `--grep` selects tests that match a regex; the `--invert` omits them.

Valid tags:

* `@SauceLabsKiller`: These tests use so much network chatter that they'll time
  out for any reasonable timeout.
