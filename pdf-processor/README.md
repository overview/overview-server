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

split-pdf-and-extract-text
==========================

Usage: `split-pdf-and-extract-text --only-extract=true IN-FILE.pdf`

Opens `IN-FILE.pdf` as a PDF. Streams it to a proprietary, streaming-friendly
_binary_ output format on stdout:

    HEADER
    PAGE
    PAGE
    ...
    FOOTER

Where `HEADER` is:

    0x1       [ 1 byte ]
    nPages    [ 4-byte integer, big-endian ]

`PAGE` is:
    0x2       [ 1 byte ]
    isOcr     [ boolean, 1 byte, either 0x0 or 0x1 ]
    thumbLen  [ 4-byte integer, big-endian, may be 0x00000000 ]
    thumbnail [ png-encoded thumbnail, may be empty ]
    pdfLen    [ 4-byte integer, big-endian, may be 0x00000000 ]
    pdf       [ pdf-encoded single page, may be empty ]
    textLen   [ 4-byte integer, big-endian, may be 0x00000000 ]
    text      [ utf8-encoded text from the page ]

And `FOOTER` is:

    0x3       [ 1 byte ]
    len       [ 4-byte integer, big-endian, may be 0x00000000 ]
    error     [ utf8-encoded text describing error; empty on success ]

Only `FOOTER` is actually required. In a valid stream:

* There is at most one `HEADER`
* If there is a `HEADER`, it appears before any `PAGE`
* If there is no `HEADER`, there are no `PAGE` entries
* There are at most `nPages` `PAGE` entries
* There is only one `FOOTER`

The `--only-extract` option will produce empty PNGs on all pages except the
first, and it will produce empty PDFs.

dump-split-pdf-and-extract-text-output
======================================

Usage: `split-pdf-and-extract-text IN-FILE.pdf | dump-split-pdf-and-extract-text-output OUTDIR`

Dumps the streamed data to a directory. Files will be:

* `OUTDIR/n-pages`: number of pages
* `OUTDIR/p1.is-ocr`: "true" or "false"
* `OUTDIR/p1.png`: Thumbnail (only if non-empty)
* `OUTDIR/p1.pdf`: Single page (only if non-empty)
* `OUTDIR/p1.txt`: Text (even if empty)
* `OUTDIR/error.txt`: Error (only if non-empty)
