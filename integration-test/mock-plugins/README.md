To Run A Simple Plugin
======================

In a test:

```javascript

const MockPlugin = require('./lib/MockPlugin')

const plugin = new MockPlugin('PLUGIN_NAME')
await plugin.listen()
// ... do stuff, assuming http://localhost:3333 is listening, and
// `GET /show` will return `PLUGIN_NAME.html` from this directory
await plugin.close()
// ... do stuff, assuming http://localhost:3333 is gone
```
