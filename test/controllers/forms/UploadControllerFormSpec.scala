package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class UploadControllerFormSpec extends Specification {

  "UploadControllerForm" should {
    
    trait FormScope extends Scope {
      val form = UploadControllerForm()
    }
    
    "fail on unsupported language" in new FormScope {
      val data = Map("lang" -> "Not a valid lang")
      
      form.bind(data).error("lang") must beSome  
    }
    
    "return lang if supported" in new FormScope {
      val lang = "sv"
      val data = Map("lang" -> lang)
      
      form.bind(data).value.map(_._1) must beSome(lang)
    }
    
    "return supplied stop words is provided" in new FormScope {
      val stopWords = "the and foo"
      val data = Map("lang" -> "en", "supplied_stop_words" -> stopWords)
      
      form.bind(data).value.map(_._2).flatten must beSome(stopWords)
    }

    "return important words is provided" in new FormScope {
      val importantWords = "these words and \\wRegexes[\\d+] REALLY Matter"
      val data = Map("lang" -> "en", "important_words" -> importantWords)
      
      form.bind(data).value.map(_._3).flatten must beSome(importantWords)
    }
  }
}