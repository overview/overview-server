package org.overviewproject.util

import org.specs2.mutable.Specification

class ConfigurationSpec extends Specification {

  
  "Configuration" should {
    
    "have MaxDocuments value" in {
      Configuration.maxDocuments must be equalTo(20000)
    }
    
    
  }
}