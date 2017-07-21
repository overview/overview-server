#!/usr/bin/env python3

import argparse
from pathlib import Path
import os.path
import re
import shutil
import sys

PDFJS_REPO = 'https://github.com/overview/overview-pdf-reader'
DEBUG = True

def eprint(*args, **kwargs):
    if DEBUG:
        print(*args, file=sys.stderr, **kwargs)

def debug(*args, **kwargs):
    eprint(*args, **kwargs)

def error(message):
    eprint('ERROR: %s' % message)
    eprint('')
    eprint('Here is how to run this program if you keep code in ~/src:')
    eprint('')
    eprint('1. cd ~/src && git clone %s.git' % PDFJS_REPO)
    eprint('2. cd overview-pdf-reader')
    eprint('3. npm install')
    eprint('4. node_modules/.bin/gulp dist')
    eprint('5. cd ~/src/overview-server')
    eprint('6. auto/refresh-pdfjs.py ~/src/overview-pdf-reader')
    sys.exit(1)

def get_overview_dirname():
    ret = os.path.dirname(os.path.dirname(__file__))
    if ret == '':
        return './web'
    else:
        return ret + '/web'

def check_pdfjs_dirname_is_pdfjs_dir(pdfjs_dirname):
    if not os.path.exists('%s/pdfjs.config' % pdfjs_dirname):
        error('Directory %s is missing pdfjs.config: it is obviously not a valid pdfjs checkout.' % pdfjs_dirname)

def check_pdfjs_build_dir(pdfjs_dirname):
    if not os.path.exists('%s/build' % pdfjs_dirname):
        error('pdfjs is missing build directory. Did you gulp dist?')

def check_pdfjs_dist_dir(pdfjs_dirname):
    if not os.path.exists('%s/build/dist' % pdfjs_dirname):
        error('pdfjs is missing build/dist directory. Did you gulp dist?')

def copy(source_dir, glob, destination_dir):
    debug('cp %s/%s %s/' % (source_dir, glob, destination_dir))
    if not Path(destination_dir).exists():
        Path(destination_dir).mkdir(parents=True)

    for source in Path(source_dir).glob(glob):
        shutil.copy(str(source), destination_dir)

def copy_document_show_scala(source, destination):
    debug('cp %s %s (modifying as we go)' % (source, destination))

    data = Path(source).read_bytes()

    # Nix DOCTYPE and comment
    data = re.sub(rb'^.*?-->', '', data)

    # Add Scala method signature
    data = b'@this(assets: AssetsFinder)\n@(document: com.overviewdocs.models.Document)' + data

    # Add <base>
    data = data.replace(b'<head>', b'<head><base href="/assets/pdfjs/web/x">')

    # Set up PDFJS paths
    data = data.replace(b'<script', b'<script>window.PDFJS = { workerSrc: "@assets.path("pdfjs/build/pdf.worker.js")" };</script><script', 1)
    data = data.replace(b'"../build/pdf.js"', b'"@assets.path("pdfjs/build/pdf.js")"', 1)
    data = data.replace(b'"viewer.js"', b'"@assets.path("pdfjs/web/viewer.js")"', 1)

    # Add onload
    data = data.replace(b'</body>', b"""<script>document.addEventListener('DOMContentLoaded', function() { PDFViewerApplication.open("@document.viewUrl", null); }, true);</script></body>""")

    Path(destination).write_bytes(data)

def run(pdfjs_dirname):
    overview_dirname = get_overview_dirname()

    check_pdfjs_dirname_is_pdfjs_dir(pdfjs_dirname)
    check_pdfjs_build_dir(pdfjs_dirname)
    check_pdfjs_dist_dir(pdfjs_dirname)

    debug('rm -r %s/public/pdfjs' % overview_dirname)
    if Path('%s/public/pdfjs' % overview_dirname).exists():
        shutil.rmtree('%s/public/pdfjs' % overview_dirname)

    for source_dir, glob, destination_dir in [
        ('build/minified/build', 'pdf.js', 'build'),
        ('build/minified/build', 'pdf.worker.js', 'build'),
        ('build/minified/build', 'pdf.js.map', 'build'),
        ('build/minified/build', 'pdf.worker.js.map', 'build'),
        ('build/minified/web', 'viewer.css', 'web'),
        ('build/minified/web', 'viewer.js', 'web'),
        ('build/minified/web/images', '*.gif', 'web/images'),
        ('build/minified/web/images', '*.png', 'web/images'),
        ('build/minified/web/cmaps', '*.bcmap', 'web/cmaps'),
    ]:
        copy(
            '%s/%s' % (pdfjs_dirname, source_dir),
            glob,
            '%s/public/pdfjs/%s' % (overview_dirname, destination_dir)
        )

    copy_document_show_scala(
        '%s/build/minified/web/viewer.html' % pdfjs_dirname,
        '%s/app/views/Document/show.scala.html' % overview_dirname
    )

def main():
    parser = argparse.ArgumentParser(description='Copy (and tweak) pdfjs reader into this repository')
    parser.add_argument('dirname', metavar='DIRNAME', type=str,
                        help=('a local clone of %s.git' % PDFJS_REPO))
    args = parser.parse_args()
    namespace = parser.parse_args()
    run(namespace.dirname)

if __name__ == '__main__':
    main()
