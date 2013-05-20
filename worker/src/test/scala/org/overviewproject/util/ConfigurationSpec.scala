package org.overviewproject.util

import org.specs2.mutable.Specification

class ConfigurationSpec extends Specification {

  
  "Configuration" should {
    
    "have MaxDocuments value" in {
      Configuration.maxDocuments must be equalTo(50000)
    }
    
    "have pageSize value" in {
      Configuration.pageSize must be equalTo(50)
    }
    
    "have maxInFlightRequests value" in {
      Configuration.maxInFlightRequests must be equalTo(4)
    }
  }
}
