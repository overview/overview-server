package org.overviewproject.runner

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.{Await, ExecutionContext, promise}
import scala.concurrent.duration.Duration

class SuperServerSpec extends Specification {
  trait BaseScope extends Scope {
    import ExecutionContext.Implicits.global

    class MockDaemonProcess extends DaemonProcess {
      val statusCode = promise[Int]

      override def destroy = statusCode.success(0)
      override def waitFor = statusCode.future
    }

    def buildDaemon = new MockDaemonProcess

    def buildDaemonProcesses : Seq[MockDaemonProcess] = Seq(buildDaemon, buildDaemon, buildDaemon)
    lazy val daemonProcesses = buildDaemonProcesses
    lazy val subject = new SuperServer(daemonProcesses.toSet)
  }

  "SuperServer" should {
    "start with all incomplete" in new BaseScope {
      subject.notCompleted must beEqualTo(daemonProcesses.toSet)
    }

    "start with nothing completed" in new BaseScope {
      subject.completed must beEqualTo(Set())
    }

    "transition an item to completed" in new BaseScope {
      daemonProcesses(1).statusCode.success(10)
      subject.completed must beEqualTo(Set(daemonProcesses(1) -> 10))
    }

    "transition an item out of notCompleted" in new BaseScope {
      daemonProcesses(1).statusCode.success(10)
      subject.notCompleted must beEqualTo(Set(daemonProcesses(0), daemonProcesses(2)))
    }

    "not complete waitForFirst before a status code is available" in new BaseScope {
      subject.waitForFirst.isCompleted must beEqualTo(false)
    }

    "return from waitForFirst when a status code is available" in new BaseScope {
      val x = subject.waitForFirst
      daemonProcesses(2).statusCode.success(0)
      x.value.get.get must beEqualTo((daemonProcesses(2) -> 0))
    }

    "not complete waitForAll before all status codes are available" in new BaseScope {
      daemonProcesses(1).statusCode.success(1)
      daemonProcesses(0).statusCode.success(0)
      subject.waitForAll.isCompleted must beEqualTo(false)
    }

    "complete  waitForAll when all status codes are available" in new BaseScope {
      daemonProcesses(1).statusCode.success(1)
      daemonProcesses(2).statusCode.success(2)
      daemonProcesses(0).statusCode.success(0)
      val result = Await.result(subject.waitForAll, Duration(10, "milliseconds"))
      result must beEqualTo(Seq(
        daemonProcesses(0) -> 0,
        daemonProcesses(1) -> 1,
        daemonProcesses(2) -> 2
      ))
    }
  }
}
