package org.overviewproject.util

import org.overviewproject.test.Specification
import org.overviewproject.util.DocumentSetCreationJobStateDescription._
import org.overviewproject.util.Progress.{ Progress => Prog }
import org.specs2.specification.Scope

class ThrottledProgressReporterSpec extends Specification {

  class ProgressReceiver {
    var notifications: Int = 0

    def countNotifications(progress: Prog) { notifications += 1 }
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
      reporter.update(Prog(0.1, Clustering))

      stateChange.notifications must be equalTo (1)
    }

    "only update if fraction completion changes by .1%" in new UpdateContext {
      val progFractions = Seq(0.1, 0.1005, 0.1021)

      progFractions.foreach(f => reporter.update(Prog(f, Clustering)))

      stateChange.notifications must be equalTo (2)
    }

    "update if state changes regardless of progress" in new UpdateContext {
      reporter.update(Prog(0.1, Clustering))
      reporter.update(Prog(0.1001, Saving))

      stateChange.notifications must be equalTo (2)
    }

    "update if sufficient time has passed" in new FrequentUpdateContext {
      reporter.update(Prog(0.1, Clustering))
      Thread.sleep(ShortTime * 2)
      reporter.update(Prog(0.1001, Clustering))

      intervalPassed.notifications must be equalTo (2)
    }

    "don't update if sufficient time has not passed" in new InfrequentUpdateContext {
      reporter.update(Prog(0.1, Clustering))
      reporter.update(Prog(0.1001, Clustering))

      intervalPassed.notifications must be equalTo (1)
    }
    
    "treat states with parameters as the same state" in new UpdateContext {
      reporter.update(Prog(0.1, Retrieving(100, 1000)))
      reporter.update(Prog(0.1001, Retrieving(101, 1000)))
      
      stateChange.notifications must be equalTo (1)
    }
  }

}