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
      
      form.bind(data).value must beSome(lang)
    }
  }
}