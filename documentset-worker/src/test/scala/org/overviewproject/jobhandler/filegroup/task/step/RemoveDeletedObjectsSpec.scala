package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import akka.actor.ActorSelection
import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestProbe
import org.specs2.mutable.Before
import akka.actor.ActorRef
import org.overviewproject.test.ForwardingActor
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import scala.concurrent.ExecutionContext

class RemoveDeletedObjectsSpec extends Specification {

  "RemoveDeletedObject" should {

    "notify fileRemovalQueue" in new RemovalContext {
      removeDeletedObjects.execute

      fileRemoval.expectMsg(RemoveFiles)
    }

    "notify fileGroupRemovalQueue" in new RemovalContext {
      removeDeletedObjects.execute

      fileGroupRemoval.expectMsg(RemoveFileGroup(fileGroupId))
    }

    "return FinalStep" in new RemovalContext {
      val next = removeDeletedObjects.execute

      next must beEqualTo(FinalStep).await
    }
  }

  abstract class RemovalContext extends ActorSystemContext with Before {
    val fileGroupId = 1l
    var fileRemoval: TestProbe = _
    var fileGroupRemoval: TestProbe = _

    var removeDeletedObjects: RemoveDeletedObjects = _

    override def before = {
      fileRemoval = TestProbe()
      fileGroupRemoval = TestProbe()
      val fileRemovalQueue = system.actorOf(ForwardingActor(fileRemoval.ref))
      val fileGroupRemovalQueue = system.actorOf(ForwardingActor(fileGroupRemoval.ref))
      removeDeletedObjects = new TestRemoveDeletedObjects(fileGroupId,
        fileRemovalQueue.path.toString, fileGroupRemovalQueue.path.toString)
    }

    class TestRemoveDeletedObjects(override protected val fileGroupId: Long,
                                   fileRemovalQueuePath: String, fileGroupRemovalQueuePath: String) extends RemoveDeletedObjects {
      override protected val fileRemovalQueue = system.actorSelection(fileRemovalQueuePath)
      override protected val fileGroupRemovalQueue = system.actorSelection(fileGroupRemovalQueuePath)
    }
  }
}