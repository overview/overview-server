package org.overviewproject.documentcloud

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import org.specs2.mutable.After
import org.specs2.specification.Scope

class RequestRetryTimesSpec extends Specification with NoTimeConversions {
  
  "RequestRetryTimes" should {
    
    trait DefaultTimesContext extends Scope {
      val defaultTimes = RequestRetryTimes()
    }

    "return default values" in new DefaultTimesContext {
      val expectedTimes = Seq(1 second, 30 seconds, 1 minute)
      val times = Seq.tabulate(4)(n => defaultTimes(n)).flatten
      
      times must be equalTo(expectedTimes)
    }
    
    "return None if no time specified for attempt" in new DefaultTimesContext {
      defaultTimes(10) must beNone
    }
  }
}