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
import org.specs2.mutable.Before

class DaughterShell(core: ActorRef, jobMonitor: ActorRef) extends Actor {
  def receive = {
    case command: ProcessFileCommand => core ! command
    case status => jobMonitor ! status
  }
}

class MotherWorkerSpec extends Specification with Mockito {

  class TestMotherWorker(daughters: Seq[ActorRef]) extends MotherWorker with FileGroupJobHandlerComponent {

    private var daughterCount = 0

    override def createFileGroupMessageHandler(jobMonitor: ActorRef): Props = {
      val daughter = daughters(daughterCount)
      daughterCount += 1

      Props(new DaughterShell(daughter, jobMonitor))
    }

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

    trait ProbeSetup {
      def createDaughters(implicit a: ActorSystem): Seq[TestProbe] = Seq.fill(2)(TestProbe())
      def createMother(daughterProbes: Seq[TestProbe])(implicit a: ActorSystem): TestActorRef[TestMotherWorker] =
        TestActorRef(new TestMotherWorker(daughterProbes.map(_.ref)))
    }

    abstract class MotherSetup extends ActorSystemContext with Before {
      var daughters: Seq[TestProbe] = _
      var motherWorker: TestActorRef[TestMotherWorker] = _

      def before = {
        daughters = Seq.fill(2)(TestProbe())
        motherWorker = TestActorRef(new TestMotherWorker(daughters.map(_.ref)))
      }
    }

    "start 2 FileGroupMessageHandlers" in new MotherSetup {
      motherWorker.underlyingActor.numberOfChildren must be equalTo (2)
    }

    "send ProcessFile message to file group message handlers" in new MotherSetup {
      val uploadedFileId = 10l

      motherWorker ! ProcessFileCommand(fileGroupId, uploadedFileId)

      daughters(0).expectMsg(ProcessFileCommand(fileGroupId, uploadedFileId))
    }

    "don't forward ProcessFile messages if all message handlers are busy" in new MotherSetup {
      val uploadedFileId = 10l

      val messages = Seq.tabulate(3)(n => ProcessFileCommand(fileGroupId, uploadedFileId + n))

      messages.foreach { msg => motherWorker ! msg }

      daughters.flatMap(_.receiveN(1)) must haveTheSameElementsAs(messages.take(2))
      daughters.map(_.expectNoMsg)
    }

    "forward queued ProcessFile message when message handlers become free" in new MotherSetup {
      val uploadedFileId = 10l
      val messages = Seq.tabulate(3)(n => ProcessFileCommand(fileGroupId, uploadedFileId + n))

      motherWorker.underlyingActor.storage.findDocumentSetCreationJobByFileGroupId(any) returns None

      messages.foreach { msg => motherWorker ! msg }

      daughters.flatMap(_.receiveN(1))

      daughters(1).reply(JobDone(messages(1).fileGroupId))
      daughters(0).expectNoMsg
      daughters(1).expectMsg(messages(2))

    }

    "create job when StartClustering is received but FileGroup is not complete" in new MotherSetup {
      val fileGroup = mock[FileGroup]
      fileGroup.id returns fileGroupId
      fileGroup.state returns InProgress
      fileGroup.userEmail returns userEmail

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns Some(fileGroup)
      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)

      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetUser(documentSetId, userEmail)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, Preparing, lang, stopWords)

      expectMsg(MessageHandled)
    }

    "create job when StartClustering is received but all files have not been processed" in new MotherSetup {
      val numberOfUploads = 5

      val fileGroup = mock[FileGroup]
      fileGroup.id returns fileGroupId
      fileGroup.state returns Complete

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns Some(fileGroup)
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns (numberOfUploads - 1)

      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)
      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, Preparing, lang, stopWords)

    }

    "submit a job when StartClustering is received and all files have been processed" in new MotherSetup {
      val numberOfUploads = 5

      val fileGroup = mock[FileGroup]
      fileGroup.id returns fileGroupId
      fileGroup.state returns Complete

      val storage = motherWorker.underlyingActor.storage
      storage.findFileGroup(fileGroupId) returns Some(fileGroup)
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads

      storage.storeDocumentSet(title, lang, stopWords) returns documentSetId

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)
      there was one(storage).storeDocumentSet(title, lang, stopWords)
      there was one(storage).storeDocumentSetCreationJob(documentSetId, fileGroupId, NotStarted, lang, stopWords)
    }

    "submit a job when JobDone for the last processed file is received and StartClustering has been received" in new MotherSetup {
      val numberOfUploads = 5

      val documentSetCreationJob = DocumentSetCreationJob(
        id = 1l,
        documentSetId = 10l,
        jobType = FileUpload,
        fileGroupId = Some(fileGroupId),
        state = Preparing)


      val storage = motherWorker.underlyingActor.storage
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads
      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId) returns Some(documentSetCreationJob)

      motherWorker ! JobDone(fileGroupId)

      there was one(storage).submitDocumentSetCreationJob(documentSetCreationJob)
    }
  }
}