#!/bin/sh

set -e
createuser overview
createdb --owner=overview --locale=C --encoding=UTF-8 overview
createdb --owner=overview --locale=C --encoding=UTF-8 overview-dev
createdb --owner=overview --locale=C --encoding=UTF-8 overview-test
