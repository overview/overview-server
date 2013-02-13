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
      val stateChange = new ProgressReceiver
      val reporter = new ThrottledProgressReporter(Seq(stateChange.countNotifications), Seq())
    }

    trait FrequentUpdateContext extends Scope {
      val ShortTime = 10l
      val intervalPassed = new ProgressReceiver
      val reporter = new ThrottledProgressReporter(Seq(), Seq(intervalPassed.countNotifications), updateInterval = ShortTime)
    }

    trait InfrequentUpdateContext extends Scope {
      val LongTime = 10000l
      val intervalPassed = new ProgressReceiver
      val reporter = new ThrottledProgressReporter(Seq(), Seq(intervalPassed.countNotifications), updateInterval = LongTime)
    }

    "update on initial state change" in new UpdateContext {
      reporter.update(Progress(0.1, Clustering))

      stateChange.notifications must be equalTo (1)
    }

    "only update if fraction complete changes in tenth's place" in new UpdateContext {
      val progFractions = Seq(0.1, 0.15, 0.2)

      progFractions.foreach(f => reporter.update(Progress(f, Clustering)))

      stateChange.notifications must be equalTo (2)
    }

    "update if state changes regardless of progress" in new UpdateContext {
      reporter.update(Progress(0.1, Clustering))
      reporter.update(Progress(0.1001, Saving))

      stateChange.notifications must be equalTo (2)
    }

    "update if sufficient time has passed" in new FrequentUpdateContext {
      reporter.update(Progress(0.1, Clustering))
      Thread.sleep(ShortTime * 2)
      reporter.update(Progress(0.1001, Clustering))

      intervalPassed.notifications must be equalTo (2)
    }

    "don't update if sufficient time has not passed" in new InfrequentUpdateContext {
      reporter.update(Progress(0.1, Clustering))
      reporter.update(Progress(0.1001, Clustering))

      intervalPassed.notifications must be equalTo (1)
    }
  }

}