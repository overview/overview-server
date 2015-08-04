package com.overviewdocs.jobhandler.filegroup.task

import scala.concurrent.Promise
import scala.concurrent.duration.DurationInt
import org.specs2.mutable.After
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions
import akka.actor.ActorSystem
import scala.concurrent.Await
import scala.concurrent.TimeoutException
import scala.util.Failure
import scala.concurrent.duration.Duration

class TimeoutGeneratorSpec extends Specification with NoTimeConversions {

  "TimeoutGenerator" should {

    "let command complete before timeout" in new SchedulerScope {
      val commandResult = 1
      command.success(commandResult)

      val r = timeoutGenerator.runWithTimeout(timeout, recordTimeout, command.future)

      r must be_==(commandResult).await
      commandTimedOut must beFalse
    }

    "timeout slow commands" in new SchedulerScope {
      val r = timeoutGenerator.runWithTimeout(timeout, recordTimeout, command.future)

      Await.ready(r, Duration.Inf)
      r.isCompleted must beTrue
      commandTimedOut must beTrue
    }
  }

  trait SchedulerScope extends After {
    val system = ActorSystem()
    val timeout = 1 millis
    val timeoutGenerator = new TimeoutGenerator(system.scheduler, system.dispatcher)

    val command = Promise[Int]
    var commandTimedOut = false

    def recordTimeout = commandTimedOut = true

    override def after = system.shutdown()
  }

}