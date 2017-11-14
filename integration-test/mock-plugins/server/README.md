The simplest file-based web server we can think of.

Maintenance
===========

We use straight Docker, not Docker Compose or even Dockerfiles.

Run `make` to recompile `./serve`, which is a Linux binary Docker can run.

Usage
=====

First, create `show.html`, an HTML file.

Now, you _can_ use a Dockerfile:

```Dockerfile
FROM scratch
COPY serve /
COPY show.html /
RUN /serve
```

Alternatively, you can use Docker directly, saving yourself the management
woes. For instance:

```sh
docker run --rm \
    --publish 3333:80 \
    -v "$(pwd)/../server/serve:/serve" \
    -v "$(pwd)/show.html:show.html" \
    busybox \
    /serve
```

What the server does
====================

The server is an HTTP server listening on port 80 of the container. It responds
to two endpoints:

* `/metadata`: a CORS-ready endpoint returning `{}`
* `/show?foo=bar&baz=moo`: the contents of `show.html`.

Any other URL else gives a descriptive 404 error.

It logs all requests.
