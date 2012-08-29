package models.util

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PasswordTesterSpec extends Specification {
  
  "PasswordTester" should {
    
    "count character space for ASCII lowercase" in {
      val tester = new PasswordTester("justlowercase")
      
      tester.countCharacterSpace must be equalTo(26)
    }
    
    "combine character space counts" in {
      val tester = new PasswordTester("lowercase UPPERCASE and SPACE")
      
      tester.countCharacterSpace must be equalTo(26 + 26 + 1)
    }
  }
}
