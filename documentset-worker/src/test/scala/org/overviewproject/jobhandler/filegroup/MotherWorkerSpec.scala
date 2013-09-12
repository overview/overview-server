package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import akka.actor._
import org.overviewproject.test.ForwardingActor
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import akka.testkit.TestProbe

class DummyActor extends Actor {
  def receive = {
    case _ =>
  }
}

object DummyActor {
  def apply(): Props = Props[DummyActor]
}

class MotherWorkerSpec extends Specification {

  class TestMotherWorker(fileGroupJobHandler: ActorRef) extends MotherWorker with FileGroupJobHandlerComponent {

    override def createFileGroupJobHandler: Props = DummyActor()

    def numberOfChildren: Int = context.children.size
  }

  "MotherWorker" should {

    "create 2 FileGroupJobHandlers" in new ActorSystemContext {
      val fileGroupJobHandler = TestProbe()

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      motherWorker.underlyingActor.numberOfChildren must be equalTo (2)
    }
  }
}