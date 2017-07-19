package com.overviewdocs.akkautil

import akka.actor.{Actor,ActorRef}
import akka.testkit.{TestActorRef,TestProbe}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import com.overviewdocs.test.ActorSystemContext

class BrokerActorSpec extends Specification {
  trait BaseScope extends Scope with ActorSystemContext {
    case class TestMessage(foo: String)

    val broker = TestActorRef(new BrokerActor[TestMessage])
    val worker1 = TestProbe()
    val worker2 = TestProbe()
    val sender = TestProbe()
  }

  "BrokerActor" should {
    "queue work then send it when worker asks" in new BaseScope {
      broker.tell(BrokerActor.Work(TestMessage("foo"), sender.ref), sender.ref)
      expectNoMsg
      broker.tell(BrokerActor.WorkerReady, worker1.ref)
      worker1.expectMsg(BrokerActor.Work(TestMessage("foo"), sender.ref))
      expectNoMsg
    }

    "queue worker then send message when work arrives" in new BaseScope {
      broker.tell(BrokerActor.WorkerReady, worker1.ref)
      expectNoMsg
      broker.tell(BrokerActor.Work(TestMessage("foo"), sender.ref), sender.ref)
      worker1.expectMsg(BrokerActor.Work(TestMessage("foo"), sender.ref))
      expectNoMsg
    }

    "queue multiple messages" in new BaseScope {
      broker.tell(BrokerActor.Work(TestMessage("foo"), sender.ref), sender.ref)
      broker.tell(BrokerActor.Work(TestMessage("bar"), sender.ref), sender.ref)
      expectNoMsg
      broker.tell(BrokerActor.WorkerReady, worker1.ref)
      worker1.expectMsg(BrokerActor.Work(TestMessage("foo"), sender.ref))
      expectNoMsg
      broker.tell(BrokerActor.WorkerReady, worker2.ref)
      worker2.expectMsg(BrokerActor.Work(TestMessage("bar"), sender.ref))
      expectNoMsg
    }

    "queue multiple workers" in new BaseScope {
      broker.tell(BrokerActor.WorkerReady, worker1.ref)
      broker.tell(BrokerActor.WorkerReady, worker2.ref)
      expectNoMsg
      broker.tell(BrokerActor.Work(TestMessage("foo"), sender.ref), sender.ref)
      worker1.expectMsg(BrokerActor.Work(TestMessage("foo"), sender.ref))
      expectNoMsg
      broker.tell(BrokerActor.Work(TestMessage("bar"), sender.ref), sender.ref)
      worker2.expectMsg(BrokerActor.Work(TestMessage("bar"), sender.ref))
      expectNoMsg
    }
  }
}
