package models

import org.specs2.mutable.Specification

class ArchiveEntrySpec extends Specification {
  "ensureUtf8FilenameHasExtension" should {
    def sanitize(filename: String, extension: String): String = {
      val filenameUtf8 = filename.getBytes("utf-8")
      val extensionUtf8 = extension.getBytes("utf-8")
      val outUtf8 = ArchiveEntry.sanitizeFilenameUtf8WithExtension(filenameUtf8, extensionUtf8)
      new String(outUtf8, "utf-8")
    }

    "leave an Array alone" in {
      sanitize("foo.pdf", ".pdf") must beEqualTo("foo.pdf")
    }

    "only change the last extension" in {
      sanitize("foo.txt.doc", ".pdf") must beEqualTo("foo.txt.pdf")
    }

    "modify a 1-letter extension" in {
      sanitize("main.c", ".pdf") must beEqualTo("main.pdf")
    }

    "modify a 2-letter extension" in {
      sanitize("README.md", ".pdf") must beEqualTo("README.pdf")
    }

    "modify a 3-letter extension" in {
      sanitize("foo.txt", ".pdf") must beEqualTo("foo.pdf")
    }

    "modify a 4-letter extension" in {
      sanitize("foo.jpeg", ".pdf") must beEqualTo("foo.pdf")
    }

    "append if there is no extension" in {
      sanitize("foo", ".pdf") must beEqualTo("foo.pdf")
    }

    "append if there is a 5-letter extension" in {
      sanitize("foo.2015-11-17", ".pdf") must beEqualTo("foo.2015-11-17.pdf")
    }

    "append a '-p3.pdf' extension" in {
      sanitize("foo.c", "-p3.pdf") must beEqualTo("foo-p3.pdf")
    }

    "nix special characters" in {
      sanitize("foo?%*:|\"<>\u0000\u0001\u001e\u001f ", ".pdf") must beEqualTo("foo____________ .pdf")
    }

    "preserve UTF-8 characters" in {
      sanitize("éȩû.txt", ".pdf") must beEqualTo("éȩû.pdf")
    }

    "turn backslashes into forward slashes" in {
      // https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT, 4.4.17
      // "All slashes must be forward slashes"
      sanitize("foo\\bar/baz", ".pdf") must beEqualTo("foo/bar/baz.pdf")
    }
  }
}
