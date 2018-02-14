To Run A Simple Plugin
======================

In a test
---------

```javascript

const MockPlugin = require('./lib/MockPlugin')

const plugin = new MockPlugin('PLUGIN_NAME')
await plugin.listen()
// ... do stuff, assuming http://0.0.0.0:3333 is listening, and
// `GET /show` will return `PLUGIN_NAME.html` from this directory
await plugin.close()
// ... do stuff, assuming http://0.0.0.0:3333 is gone
```

Standalone (useful when building tests)
---------------------------------------

```sh
auto/docker-exec.sh integration-test/mock-plugins/standalone PLUGIN_NAME
```

The plugin will be at `http://localhost:3333`. We run it using `docker exec`,
so the Overview server on localhost will _see_ it at `http://localhost:3333`,
too.

TODO let the _plugin_ see _Overview_ -- Overview will be on port 80 inside its
container, not port 9000.
