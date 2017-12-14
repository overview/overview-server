#!/usr/bin/env python3

from contextlib import contextmanager
import os.path
import re
import shutil
import subprocess
import tempfile
import unittest

ExecDir = os.path.join(os.path.dirname(__file__), '..')
TestFilesDir = os.path.join(os.path.dirname(__file__), 'files', 'pdfs')
ImagesDir = os.path.join(os.path.dirname(__file__), 'files', 'expected-images')

class TestSplitPdfAndExtractText(unittest.TestCase):
    @contextmanager
    def split_pdf_and_extract_text(self, flag, filename):
        with tempfile.TemporaryDirectory(prefix="pdf-processor-test-pdf-and-extract-text") as tempdir:
            with subprocess.Popen([
                os.path.join(ExecDir, 'split-pdf-and-extract-text'),
                flag,
                os.path.join(TestFilesDir, filename)
            ], stdout=subprocess.PIPE) as split_process:
                with subprocess.Popen([
                    os.path.join(ExecDir, 'dump-split-pdf-and-extract-text-output'),
                    tempdir
                ], stdin=split_process.stdout) as dump_process:
                    self.assertEqual(0, dump_process.wait(), 'dump-output failed')
                    self.assertEqual(0, split_process.wait(), 'split-pdf-and-extract-text failed')

            self.tempdir = tempdir
            yield

    def assertContent(self, expect, filename):
        if isinstance(expect, str):
            expect = bytes(expect, 'utf-8')
        expect = re.sub(rb'/CreationDate\(D:[0-9]{14}\)', b'/CreationDate(D:XXXXXXXXXXXXXX)', expect)
        expect = re.sub(rb'/ID\[<[A-F0-9]{32}><[A-F0-9]{32}>\]', b'ID[<XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX><XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX>]', expect)

        path = os.path.join(self.tempdir, filename)
        self.assertTrue(
            os.path.isfile(path),
            'Output file {} is missing; only files were {}'.format(path, ','.join(os.listdir(self.tempdir)))
        )
        with open(os.path.join(self.tempdir, filename), 'rb') as f:
            actual = f.read()
        actual = re.sub(rb'/CreationDate\(D:[0-9]{14}\)', b'/CreationDate(D:XXXXXXXXXXXXXX)', actual)
        actual = re.sub(rb'/ID\[<[A-F0-9]{32}><[A-F0-9]{32}>\]', b'ID[<XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX><XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX>]', actual)
        if actual != expect:
            with open('actual.bin', 'wb') as f2:
                f2.write(actual)
        self.assertEqual(expect, actual, 'Output file {} has wrong contents'.format(filename))

    def assertNoOutput(self, filename):
        path = os.path.join(self.tempdir, filename)
        self.assertFalse(
            os.path.isfile(path),
            'Output file {} should not exist; it is one of {}'.format(path, ','.join(os.listdir(self.tempdir)))
        )

    def test_outputs_text_pages(self):
        with self.split_pdf_and_extract_text('--only-extract=true', '2-pages.pdf'):
            self.assertContent('2', 'n-pages')
            self.assertContent('Page 1', 'p1.txt')
            self.assertContent('Page 2', 'p2.txt')

    def test_generates_p1_thumbnail(self):
        with open(os.path.join(ImagesDir, '2-pages.pdf-p1.png'), 'rb') as f:
            page1Png = f.read()
        with self.split_pdf_and_extract_text('--only-extract=true', '2-pages.pdf'):
            self.assertContent(page1Png, 'p1.png')

    def test_generates_no_p2_thumbnail(self):
        with self.split_pdf_and_extract_text('--only-extract=true', '2-pages.pdf'):
            self.assertNoOutput('p2.png')

    def test_no_error(self):
        with self.split_pdf_and_extract_text('--only-extract=true', '2-pages.pdf'):
            self.assertNoOutput('error.txt')

    def test_error_encrypted(self):
        with self.split_pdf_and_extract_text('--only-extract=true', 'empty-page-encrypted.pdf'):
            self.assertNoOutput('n-pages')
            self.assertContent('Failed to open PDF: file is password-protected', 'error.txt')

    def test_error_invalid_pdf(self):
        with self.split_pdf_and_extract_text('--only-extract=true', 'not-a-pdf.pdf'):
            self.assertNoOutput('n-pages')
            self.assertContent('Failed to open PDF: file is not a valid PDF', 'error.txt')

    def test_owner_protected_pdf(self):
        with self.split_pdf_and_extract_text('--only-extract=true', 'owner-protected.pdf'):
            self.assertContent('1', 'n-pages')
            self.assertContent('foo', 'p1.txt')
            self.assertNoOutput('error.txt')

    def test_empty_page(self):
        with self.split_pdf_and_extract_text('--only-extract=true', 'empty-page.pdf'):
            self.assertContent('1', 'n-pages')
            self.assertContent('', 'p1.txt')
            self.assertNoOutput('error.txt')

    def test_recoverable_error_on_pdf_p2(self):
        with self.split_pdf_and_extract_text('--only-extract=true', '2nd-page-invalid.pdf'):
            self.assertContent('2', 'n-pages')
            self.assertContent('Page 1', 'p1.txt')
            # pdfium will gracefully handle errors in the PDF: it will just nix
            # the bugs, kinda like a flexible HTML parser.
            self.assertContent('', 'p2.txt')
            self.assertNoOutput('error.txt')

    def test_is_ocr(self):
        with self.split_pdf_and_extract_text('--only-extract=true', 'ocr-on-p1.pdf'):
            self.assertContent('true', 'p1.is-ocr')
            self.assertContent('false', 'p2.is-ocr')
            self.assertContent('Page 1', 'p1.txt') # annotations are invisible, right?

    #TODO def test_unrecoverable_error_on_pdf_p2(self):
    # How to test this? Let's try a normalized PDF that's been truncated.

    def test_split_thumbnails(self):
        with self.split_pdf_and_extract_text('--only-extract=false', '2-pages.pdf'):
            with open(os.path.join(ImagesDir, '2-pages.pdf-p1.png'), 'rb') as f:
                png = f.read()
                self.assertContent(png, 'p1.png')
            with open(os.path.join(ImagesDir, '2-pages.pdf-p2.png'), 'rb') as f:
                png = f.read()
                self.assertContent(png, 'p2.png')

    def test_split_pdfs(self):
        with self.split_pdf_and_extract_text('--only-extract=false', '2-pages.pdf'):
            with open(os.path.join(TestFilesDir, '2-pages.pdf-p1.pdf'), 'rb') as f:
                png = f.read()
                self.assertContent(png, 'p1.pdf')
            with open(os.path.join(TestFilesDir, '2-pages.pdf-p2.pdf'), 'rb') as f:
                png = f.read()
                self.assertContent(png, 'p2.pdf')

if __name__ == '__main__':
    unittest.main()
