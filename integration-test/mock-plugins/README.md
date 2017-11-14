To Run A Simple Plugin
======================

```sh
docker run --rm \
    --publish 127.0.0.1:3333:80 \
    -v "$(pwd)"/server/serve:/serve:ro \
    -v "$(pwd)"/PLUGIN_NAME.html:/show.html:ro \
    busybox \
    /serve
```

... where:

* `PLUGIN_NAME` is the name of the plugin (see the HTML files in this dir); and
* `busybox` is _any_ Docker image without an entrypoint
