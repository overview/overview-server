package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import akka.actor._
import org.overviewproject.test.ForwardingActor
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ListenForFileGroupJobs
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.StartClusteringCommand
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.DocumentSetCreationJobState.Preparing
import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJob

class MotherWorkerSpec extends Specification with Mockito {

  class TestMotherWorker(fileGroupJobHandler: ActorRef) extends MotherWorker with FileGroupJobHandlerComponent {

    override def createFileGroupJobHandler: Props = ForwardingActor(fileGroupJobHandler)

    def numberOfChildren: Int = context.children.size

    override val storage = mock[Storage]
  }

  "MotherWorker" should {

    "create 2 FileGroupJobHandlers" in new ActorSystemContext {
      val fileGroupJobHandler = TestProbe()

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      motherWorker.underlyingActor.numberOfChildren must be equalTo (2)

      fileGroupJobHandler.expectMsg(ListenForFileGroupJobs)
    }

    "create job when StartClustering is received but FileGroup is not complete" in new ActorSystemContext {
      val title = "title"
      val lang = "sv"
      val stopWords = "ignore us"

      val documentSetId = 2l

      val fileGroupId = 1l

      val fileGroupJobHandler = TestProbe()
      val fileGroup = mock[FileGroup]
      fileGroup.state returns InProgress

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns fileGroup
      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)

      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, Preparing, lang, stopWords) 

    }
  }
}