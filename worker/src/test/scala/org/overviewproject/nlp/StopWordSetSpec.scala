package org.overviewproject.nlp

import org.specs2.mutable.Specification

class StopWordSetSpec extends Specification {
  
  "StopWordSet" should {
    
    "default to English stopwords file if language is empty or unknown" in {
      val stopwords: Set[String] = StopWordSet("")
      
      stopwords must contain("the")
    }
    
    "read known stopwords file" in {
      val stopWords: Set[String] = StopWordSet("sv")
      
      stopWords must contain("och")
    }
  }

}