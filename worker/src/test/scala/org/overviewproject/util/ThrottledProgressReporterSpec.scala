package org.overviewproject.util

import org.overviewproject.test.Specification
import overview.util.Progress._
import overview.util.DocumentSetCreationJobStateDescription._

class ThrottledProgressReporterSpec extends Specification {

  class ProgressReceiver {
    var notifications: Int = 0

    def countNotifications(progress: Progress) { notifications += 1 }
  }

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

  "only update if fraction complete changes in tenth's place" in {
    val reporter = new ThrottledProgressReporter
    val receiver = new ProgressReceiver

    reporter.notifyOnStateChange(receiver.countNotifications)
    reporter.update(Progress(0.1, Clustering))
    reporter.update(Progress(0.15, Clustering))
    reporter.update(Progress(0.2, Clustering))
    
    receiver.notifications must be equalTo(2)
  }

}