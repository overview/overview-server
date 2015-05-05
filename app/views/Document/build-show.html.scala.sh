#!/bin/sh

# Rebuilds show.scala.html, based on the PDFJS viewer code.
#
# Every time you update PDFJS, run this script to rebuild our version of it.
#
# (Why not use vanilla PDFJS? Because that would force us to use an ugly URL
# -- it would need a ".html" and it would need to put the full document PDF
# path in the query string. We want these URLs to be bookmarkable, so that's
# not an option.)

DIR="$(dirname "$0")"
INFILE="$DIR"/../../../public/pdfjs/web/viewer.html
OUTFILE="$DIR"/show.scala.html

echo "@(document: org.overviewproject.models.Document)" > "$OUTFILE"
cat "$INFILE" \
  | sed -e 's/^.*<!DOCTYPE/<!DOCTYPE/' \
  | sed -e 's/<head>/<head><base href="\/assets\/pdfjs\/web\/viewer.html">/' \
  | sed -e 's/@/@@/g' \
  | sed -e 's/<\/body>/<script>document.addEventListener("DOMContentLoaded", function() { PDFViewerApplication.open("@document.viewUrl", 0); }, true);<\/script><\/body>/' \
  >> "$OUTFILE"

