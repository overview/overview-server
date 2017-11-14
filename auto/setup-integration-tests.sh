#!/bin/bash

# Integration tests are _not_ run within Docker. That's because we want
# to be able to spin up plugin instances willy-nilly, using Docker

(cd "$(dirname $0)/../integration-test" && npm install)
