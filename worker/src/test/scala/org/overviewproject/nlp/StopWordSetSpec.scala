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
    
    "convert supplied stopwords to lowercase" in {
      val stopWord = "STOPWORD"
        
      val stopWords: Set[String] = StopWordSet("en", Some(stopWord))
      
      stopWords must contain(stopWord.toLowerCase)
    }
    
    "strip all non-alphanumeric chars except - and ' from supplied stopwords" in {
      val (word1, word2, word3) = ("qapla'", "7-of-9", "engage")
      val stopWordString = s"""$word1! ($word2) "$word3""""
      
      val stopWords: Set[String] = StopWordSet("en", Some(stopWordString))
      
      stopWords must contain(word1, word2, word3)
    }
  }

}