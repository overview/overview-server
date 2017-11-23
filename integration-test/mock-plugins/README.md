To Run A Simple Plugin
======================

In a test
---------

```javascript

const MockPlugin = require('./lib/MockPlugin')

const plugin = new MockPlugin('PLUGIN_NAME')
await plugin.listen()
// ... do stuff, assuming http://localhost:3333 is listening, and
// `GET /show` will return `PLUGIN_NAME.html` from this directory
await plugin.close()
// ... do stuff, assuming http://localhost:3333 is gone
```

Standalone (useful when building tests)
---------------------------------------

```sh
./auto/docker-exec.sh integration-test/mock-plugins/standalone PLUGIN_NAME
```

The plugin will be at `http://localhost:3333`; and since it's run using
`docker exec`, you know that the test Overview server will be able to
access it as `http://localhost:3333`.
