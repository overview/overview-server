package com.overviewdocs.jobhandler.filegroup

import akka.testkit.TestActorRef
import java.time.Instant
import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future,Promise}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import com.overviewdocs.test.ActorSystemContext

class ProgressReporterSpec extends Specification with Mockito {
  sequential

  import ProgressReporter._

  "ProgressReporter" should {

    trait BaseScope extends ActorSystemContext {
      val addDocumentsImpl = mock[AddDocumentsImpl]
      val anInstant: Instant = Instant.ofEpochMilli(123L)

      addDocumentsImpl.writeProgress(any, any, any, any)(any) returns Future.successful(())
      var nSchedules: Int = 0

      class TestProgressReporter()
      extends ProgressReporter(addDocumentsImpl, FiniteDuration(1, "s")) {
        override def scheduleFlush: Unit = nSchedules += 1
      }

      val subject = TestActorRef(new TestProgressReporter)
    }

    "defer any writing" in new BaseScope {
      subject ! Update(1L, 2, 3L, anInstant)
      there was no(addDocumentsImpl).writeProgress(any, any, any, any)(any)
      nSchedules must beEqualTo(1)
    }

    "write when flushing" in new BaseScope {
      val update = Update(1L, 2, 3L, anInstant)
      subject ! update
      subject ! Flush
      there was one(addDocumentsImpl).writeProgress(1L, 2, 3L, anInstant)(subject.dispatcher)
    }

    "work after flushing" in new BaseScope {
     subject ! Update(1L, 2, 3L, anInstant)
     subject ! Flush
     subject ! Update(1L, 3, 4L, anInstant)
     there was no(addDocumentsImpl).writeProgress(1L, 3, 4L, anInstant)(subject.dispatcher)
     subject ! Flush
     there was one(addDocumentsImpl).writeProgress(1L, 3, 4L, anInstant)(subject.dispatcher)
    }

    "flush two writes at once" in new BaseScope {
      subject ! Update(1L, 2, 3L, anInstant)
      subject ! Update(2L, 3, 4L, anInstant)

      subject ! Flush

      there was one(addDocumentsImpl).writeProgress(1L, 2, 3L, anInstant)(subject.dispatcher)
      there was one(addDocumentsImpl).writeProgress(2L, 3, 4L, anInstant)(subject.dispatcher)
    }

    "collapse writes to the same fileGroupId" in new BaseScope {
      subject ! Update(1L, 2, 3L, anInstant)
      subject ! Update(1L, 3, 4L, anInstant)
      subject ! Flush
      there was no(addDocumentsImpl).writeProgress(1L, 2, 3L, anInstant)(subject.dispatcher)
      there was one(addDocumentsImpl).writeProgress(1L, 3, 4L, anInstant)(subject.dispatcher)
    }

    "not flush when already flushing" in new BaseScope {
      val promise = Promise[Unit]()
      addDocumentsImpl.writeProgress(any, any, any, any)(any) returns promise.future

      subject ! Update(1L, 2, 3L, anInstant)
      subject ! Flush

      subject ! Update(1L, 3, 4L, anInstant) // while we're writing to the database

      nSchedules must beEqualTo(1)
      promise.success(())
      nSchedules must beEqualTo(2)

      there was no(addDocumentsImpl).writeProgress(1L, 3, 4L, anInstant)(subject.dispatcher)
      subject ! Flush
      there was one(addDocumentsImpl).writeProgress(1L, 3, 4L, anInstant)(subject.dispatcher)
    }
  }
}
