pdf-processor
=============

Processes PDFs for Overview Server.

Requirements:

* PDF processing happens in a separate process, so its parent can handle
  out-of-memory errors.
* It's quick.

Right now, Overview's worker runs pdf-processor as two programs:
split-pdf-and-extract-text and make-pdf-searchable. See their documentation
for descriptions of those scripts.

In the future, we may make pdf-processor daemons that grab work off a queue.
That would help us scale up when demand spikes.

Developing
==========

1. [Install Docker-CE](https://docs.docker.com/engine/installation/) and
   if you're on Linux, [install docker-compose](https://docs.docker.com/compose/install/#install-compose).
1. Install [Dazel](https://github.com/nadirizr/dazel): `pip3 install dazel`
1. Build: `dazel build //main:split-pdf-and-extract-text`
