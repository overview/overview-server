package com.overviewdocs.akkautil

import akka.actor.{Actor,ActorRef}
import akka.testkit.{TestActorRef,TestKitBase,TestProbe}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.test.ActorSystemContext

class WorkerActorSpec extends Specification {
  trait BaseScope extends Scope with ActorSystemContext {
    case class TestMessage(foo: String)

    case class IGotTheMessage(foo: String)

    val exception = new Exception("Crash!")

    val asker = TestProbe()
    val broker = TestProbe()
    val worker = TestActorRef(new WorkerActor[TestMessage](broker.ref) {
      override def doWorkAsync(message: TestMessage, asker: ActorRef): Future[Unit] = {
        if (message.foo == "crash!") {
          Future.failed(exception)
        } else {
          asker ! IGotTheMessage(message.foo)
          Future.unit
        }
      }
    })
  }

  "WorkerActor" should {
    "ask broker for a message on startup" in new BaseScope {
      broker.expectMsg(BrokerActor.WorkerReady)
    }

    "call doWorkAsync() with correct message and asker" in new BaseScope {
      worker.tell(BrokerActor.Work(TestMessage("foo"), asker.ref), broker.ref)
      asker.expectMsg(IGotTheMessage("foo"))
    }

    "ask broker for another message when done" in new BaseScope {
      broker.expectMsg(BrokerActor.WorkerReady) // the first one
      worker.tell(BrokerActor.Work(TestMessage("foo"), asker.ref), broker.ref)
      broker.expectMsg(BrokerActor.WorkerReady) // the one after work is done
    }

    "forward failure to caller and ask broker for another message" in new BaseScope with TestKitBase {
      broker.expectMsg(BrokerActor.WorkerReady) // the first one
      worker.tell(BrokerActor.Work(TestMessage("crash!"), asker.ref), broker.ref)
      asker.expectMsg(exception)
      broker.expectMsg(BrokerActor.WorkerReady) // the one after work is done
    }
  }
}
