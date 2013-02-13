package org.overviewproject.util

import org.overviewproject.test.Specification
import overview.util.Progress._
import overview.util.DocumentSetCreationJobStateDescription._

class ThrottledProgressReporterSpec extends Specification {
  
  "ThrottledProgressReporter" should {
    
    "update on state change" in {
      val reporter = new ThrottledProgressReporter
      
      var notified = false
      
      def receiveNotification(progress: Progress) {
        notified = true
      }
      reporter.notifyOnStateChange(receiveNotification)
      
      reporter.update(Progress(0.1, Clustering))
      
      notified must beTrue
    }
  }
  
  

}