package org.overviewproject.jobhandler.filegroup

import akka.actor._
import akka.testkit._
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.StartClusteringCommand
import org.overviewproject.test.{ ActorSystemContext, ForwardingActor }
import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload
import org.overviewproject.tree.orm.{ DocumentSetCreationJob, FileGroup }
import org.overviewproject.tree.orm.DocumentSetCreationJobState.{ NotStarted, Preparing }
import org.overviewproject.tree.orm.FileJobState._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.overviewproject.jobhandler.filegroup.FileGroupMessageHandlerProtocol.ProcessFileCommand


class MotherWorkerSpec extends Specification with Mockito {

  class TestMotherWorker(fileGroupJobHandler: ActorRef) extends MotherWorker with FileGroupJobHandlerComponent {

    override def createFileGroupMessageHandler(jobMonitor: ActorRef): Props = ForwardingActor(fileGroupJobHandler)

    def numberOfChildren: Int = context.children.size

    override val storage = mock[Storage]
  }

  "MotherWorker" should {
    val title = "title"
    val lang = "sv"
    val stopWords = "ignore us"
    val fileGroupId = 1l
    val documentSetId = 2l
    val userEmail = "user@email.com"

    "start 2 FileGroupMessageHandlers" in new ActorSystemContext {
      val fileGroupMessageHandler = TestProbe()

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupMessageHandler.ref))

      motherWorker.underlyingActor.numberOfChildren must be equalTo (2)
    }
    
    "forward ProcessFile message to file group message handlers" in new ActorSystemContext {
      val fileGroupJobHandler = TestProbe()
      val uploadedFileId = 10l

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      motherWorker ! ProcessFileCommand(fileGroupId, uploadedFileId)
      
      fileGroupJobHandler.expectMsg(ProcessFileCommand(fileGroupId, uploadedFileId))
    }

    "create job when StartClustering is received but FileGroup is not complete" in new ActorSystemContext {
      val fileGroupJobHandler = TestProbe()
      val fileGroup = mock[FileGroup]
      fileGroup.id returns fileGroupId
      fileGroup.state returns InProgress
      fileGroup.userEmail returns userEmail

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns Some(fileGroup)
      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)

      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetUser(documentSetId, userEmail)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, Preparing, lang, stopWords)
      
      expectMsg(MessageHandled)
    }

    "create job when StartClustering is received but all files have not been processed" in new ActorSystemContext {
      val numberOfUploads = 5

      val fileGroupJobHandler = TestProbe()
      val fileGroup = mock[FileGroup]
      fileGroup.id returns fileGroupId
      fileGroup.state returns Complete

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns Some(fileGroup)
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns (numberOfUploads - 1)

      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)
      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, Preparing, lang, stopWords)

    }
    
    "submit a job when StartClustering is received and all files have been processed" in new ActorSystemContext {
      val numberOfUploads = 5

      val fileGroupJobHandler = TestProbe()
      val fileGroup = mock[FileGroup]
      fileGroup.id returns fileGroupId
      fileGroup.state returns Complete

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns Some(fileGroup)
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads

      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)
      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, NotStarted, lang, stopWords)
    }
    
    "submit a job when JobDone for the last processed file is received and StartClustering has been received" in new ActorSystemContext {
      val numberOfUploads = 5

      val documentSetCreationJob = DocumentSetCreationJob(
        id = 1l,
        documentSetId = 10l,
        jobType = FileUpload,
        fileGroupId = Some(fileGroupId),
        state = Preparing
      )
      val fileGroupJobHandler = TestProbe()

      val motherWorker = TestActorRef(new TestMotherWorker(fileGroupJobHandler.ref))

      val storage = motherWorker.underlyingActor.storage
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads
      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId) returns Some(documentSetCreationJob)

      motherWorker ! JobDone(fileGroupId)
      
      there was one(storage).submitDocumentSetCreationJob(documentSetCreationJob)
    }
  }
}