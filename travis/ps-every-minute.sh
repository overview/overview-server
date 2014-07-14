#!/bin/sh

for i in `seq 1 48`; do
  sleep 60
  ps wx
done
