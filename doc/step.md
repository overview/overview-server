# Anatomy of a Step

As seen in [[Ingest Pipeline]], here's _Overview's_ definition of a Step: a
Step operates on a written File: it invokes a sequence of atomic database
writes that creates 0, 1 or many written and processed children. Finally, it
outputs its input as a processed File.

Fortunately, Step authors don't need to worry about states, children or
the database. They just need to convert one file at a time.

A Step is an HTTP client that runs this loop:

1. Download a Task from Overview
2. Download the blob of data related to the task (which can come from Overview
   or from S3 directly)
3. Derive new data and upload it

# Files-Only Converter

Let's build a [LibreOffice convert Step](https://github.com/overview/overview-convert-office).

## 1. `Dockerfile`

```sh
mkdir overview-convert-office
cd overview-convert-office
edit Dockerfile
```

**Dockerfile:**

Your Dockerfile should be for a multi-stage build. It should have a `test`
target (so you can use Docker Hub as a continuous integration server) and a
`production` target.

```Dockerfile
FROM overview-convert-base:1.0.0 AS base
# "base" is a lightweight framework

FROM alpine:3.7 AS dev

RUN apk add --update --no-cache libreoffice

WORKDIR /app
COPY --from=base /app/* /app/
COPY app/* app/
```

## `do-convert`

The converter
