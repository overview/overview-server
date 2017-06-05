package com.overviewdocs.searchindex

import org.specs2.mutable.Specification

class SnippetSpec extends Specification {
  "Utf16Snippet" should {
    "#tokenize" should {
      "tokenize text" in {
        val text = "the plate ran away with the spoon"
        val snippet = Utf16Snippet(0, text.length, Vector(Utf16Highlight(4, 9), Utf16Highlight(24, 27)))
        snippet.tokenize(text) must beEqualTo(Seq(
          Snippet.TextToken("the "),
          Snippet.HighlightToken("plate"),
          Snippet.TextToken(" ran away with "),
          Snippet.HighlightToken("the"),
          Snippet.TextToken(" spoon")
        ))
      }

      "show elision" in {
        val text = "the plate ran"
        val snippet = Utf16Snippet(4, 10, Vector(Utf16Highlight(4, 9)))
        snippet.tokenize(text) must beEqualTo(Seq(
          Snippet.ElisionToken,
          Snippet.HighlightToken("plate"),
          Snippet.TextToken(" "),
          Snippet.ElisionToken
        ))
      }
    }
  }

  "Snippet" should {
    "#concatTokenCollections" should {
      "flatten duplicate ElisionTokens" in {
        Snippet.concatTokenCollections(Seq(
          Seq(Snippet.TextToken("foo"), Snippet.ElisionToken),
          Seq(Snippet.ElisionToken, Snippet.TextToken("bar"))
        )) must beEqualTo(Seq(Snippet.TextToken("foo"), Snippet.ElisionToken, Snippet.TextToken("bar")))
      }
    }
  }
}
