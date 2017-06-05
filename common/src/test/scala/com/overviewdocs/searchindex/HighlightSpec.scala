package com.overviewdocs.searchindex

import org.specs2.mutable.Specification

class HighlightSpec extends Specification {
  "Utf16Highlight" should {
    "#toUtf8Highlight" should {
      "change values" in {
        // text has:
        // * a 1-byte character
        // * a 2-byte character
        // * a 3-byte character (which takes one UTF-16 Char to store)
        // * a 4-byte character (which takes two UTF-16 Chars to store)
        // * a 1-byte character
        // ... total 11 UTF-8 bytes, 6 UTF_16 Chars, 5 codepoints
        val text = "a\u00bd\u0841\ud800\udd00b"
        val highlight = Utf16Highlight(2, 6)
        highlight.toUtf8Highlight(text) must beEqualTo(Utf8Highlight(3, 11))
      }
    }
  }
}
