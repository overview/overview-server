package org.overviewproject.util

import org.overviewproject.test.Specification
import overview.util.Progress._
import overview.util.DocumentSetCreationJobStateDescription._
import org.specs2.specification.Scope

class ThrottledProgressReporterSpec extends Specification {

  class ProgressReceiver {
    var notifications: Int = 0

    def countNotifications(progress: Progress) { notifications += 1 }
  }

  "ThrottledProgressReporter" should {

    trait UpdateContext extends Scope {
      val reporter = new ThrottledProgressReporter
      val receiver = new ProgressReceiver

      reporter.notifyOnStateChange(receiver.countNotifications)
    }

    "update on initial state change" in new UpdateContext {
      reporter.update(Progress(0.1, Clustering))

      receiver.notifications must be equalTo (1)
    }

    "only update if fraction complete changes in tenth's place" in new UpdateContext {
      val progFractions = Seq(0.1, 0.15, 0.2)
      
      progFractions.foreach(f => reporter.update(Progress(f, Clustering)))

      receiver.notifications must be equalTo (2)
    }
  }

}