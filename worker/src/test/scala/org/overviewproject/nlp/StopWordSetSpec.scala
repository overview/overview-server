package org.overviewproject.nlp

import org.specs2.mutable.Specification

class StopWordSetSpec extends Specification {
  
  "StopWordSet" should {
    
    "default to English stopwords file if language is empty or unknown" in {
      val stopwords: Set[String] = StopWordSet("", None)
      
      stopwords must contain("the")
    }
    
    "read known stopwords file" in {
      val stopWords: Set[String] = StopWordSet("sv", None)
      
      stopWords must contain("och")
    }
    
    "split supplied stopwords by whitespace" in {
     val (word1, word2, word3) = ("one", "two", "three")
      val suppliedStopWordsString = s"  $word1\t  $word2\n\n$word3\n"
      
      val stopWords: Set[String] = StopWordSet("en", Some(suppliedStopWordsString))
      
      stopWords must contain(word1, word2, word3)
    }
    
  }

}