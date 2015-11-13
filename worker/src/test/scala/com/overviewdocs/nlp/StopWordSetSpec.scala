package com.overviewdocs.nlp

import org.specs2.mutable.Specification

class StopWordSetSpec extends Specification {
  "StopWordSet" should {
    "read known stopwords file" in {
      StopWordSet("sv", Seq()) must contain("och")
    }

    "convert supplied stopwords to lowercase" in {
      StopWordSet("en", Seq("STOPWORD")) must contain("stopword")
    }
  }
}
