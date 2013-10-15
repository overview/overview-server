package org.overviewproject.jobhandler.filegroup

import scala.concurrent.duration._
import scala.util.control.Exception._
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
import org.specs2.time.NoTimeConversions
import org.overviewproject.jobhandler.filegroup.MotherWorkerProtocol.CancelUploadWithDocumentSetCommand
import org.specs2.execute.PendingUntilFixed

class DaughterShell(core: ActorRef, jobMonitor: ActorRef) extends Actor {
  def receive = {
    case command: ProcessFileCommand => core ! command
    case status => jobMonitor ! status
  }
}


class FailingDaughter(jobMonitor: ActorRef, failOnMessage: Boolean) extends Actor {
  def receive = {
    case ProcessFileCommand(fileGroupId, uploadId) => {
      if (failOnMessage) context.stop(self)
      else jobMonitor ! JobDone(fileGroupId)
    }
  }
}

class WaitingDaughter(jobMonitorProbe: ActorRef) extends Actor with FSM[String, (Long, Option[ActorRef])] {
  private val Idle = "Idle"
  private val Working = "Working"
  private val FinishAtOnce = "FinishAtOnce"

  startWith("Idle", (0, None))

  when(Idle) {
    case Event(ProcessFileCommand(fileGroupId, _), _) =>
      goto(Working) using (fileGroupId, Some(sender))
    case Event(_, _) =>
      goto(FinishAtOnce) using (0, None)
  }

  when(FinishAtOnce) {
    case Event(ProcessFileCommand(fileGroupId, _), _) => {
      sender ! JobDone(fileGroupId)
      jobMonitorProbe ! JobDone(fileGroupId)

      goto(Idle) using (0, None)
    }

  }
  when(Working) {
    case Event(_, (fileGroupId, jobMonitorProxy)) => {
      jobMonitorProxy.get ! JobDone(fileGroupId)
      jobMonitorProbe ! JobDone(fileGroupId)

      goto(Idle) using (0, None)
    }
  }

  initialize

}

class MotherWorkerSpec extends Specification with Mockito with NoTimeConversions {

  class TestMotherWorker(daughters: Seq[ActorRef]) extends MotherWorker with FileGroupJobHandlerComponent {

    private var daughterCount = 0

    override def createFileGroupMessageHandler(jobMonitor: ActorRef): Props = {
      val daughter = daughters(daughterCount)
      daughterCount += 1

      Props(new DaughterShell(daughter, jobMonitor))
    }

    def numberOfChildren: Int = context.children.size

    override val storage = smartMock[Storage]
  }

  class MotherWorkerWithFailingDaughter(jobMonitorProbe: ActorRef) extends MotherWorker with FileGroupJobHandlerComponent {
    private var daughterCount = 0

    override def createFileGroupMessageHandler(jobMonitor: ActorRef): Props = {
      daughterCount += 1
      val failOnMessage = (daughterCount == 1)
      Props(new FailingDaughter(jobMonitorProbe, failOnMessage))
    }
      

    override val storage = smartMock[Storage]
    storage.countFileUploads(any) returns 10
    storage.countProcessedFiles(any) returns 5
    
    def numberOfChildren: Int = context.children.size
  }

  "MotherWorker" should {
    val Pause = 20 millis
    val title = "title"
    val lang = "sv"
    val stopWords = "ignore us"
    val fileGroupId = 1l
    val documentSetId = 2l
    val uploadedFileId = 10l
    val userEmail = "user@email.com"

    abstract class MotherSetup extends ActorSystemContext with Before {
      var daughters: Seq[TestProbe] = _
      var motherWorker: TestActorRef[TestMotherWorker] = _

      def storage = motherWorker.underlyingActor.storage

      def before = {
        daughters = Seq.fill(2)(TestProbe())
        motherWorker = TestActorRef(new TestMotherWorker(daughters.map(_.ref)))
      }
    }

    "start 2 FileGroupMessageHandlers" in new MotherSetup {
      motherWorker.underlyingActor.numberOfChildren must be equalTo (2)
    }

    "send ProcessFile message to file group message handlers" in new MotherSetup {

      motherWorker ! ProcessFileCommand(fileGroupId, uploadedFileId)

      daughters(0).expectMsg(ProcessFileCommand(fileGroupId, uploadedFileId))
    }

    "don't forward ProcessFile messages if all message handlers are busy" in new MotherSetup {

      val messages = Seq.tabulate(3)(n => ProcessFileCommand(fileGroupId, uploadedFileId + n))

      messages.foreach { msg => motherWorker ! msg }

      daughters.flatMap(_.receiveN(1)) must haveTheSameElementsAs(messages.take(2))
      daughters.map(_.expectNoMsg(Pause))
    }

    "forward queued ProcessFile message when message handlers become free" in new MotherSetup {
      val messages = Seq.tabulate(3)(n => ProcessFileCommand(fileGroupId, uploadedFileId + n))

      storage.findDocumentSetCreationJobByFileGroupId(any) returns None

      messages.foreach { msg => motherWorker ! msg }

      daughters.flatMap(_.receiveN(1))

      daughters(1).reply(JobDone(messages(1).fileGroupId))
      daughters(0).expectNoMsg(Pause)
      daughters(1).expectMsg(messages(2))
    }

    "make sure message handler gets new ProcessFile message after finishing one task" in new MotherSetup {
      storage.findDocumentSetCreationJobByFileGroupId(any) returns None
      val messages = Seq.tabulate(3)(n => ProcessFileCommand(fileGroupId, uploadedFileId + n))

      messages.take(2).foreach { msg => motherWorker ! msg }

      daughters.foreach(_.receiveN(1))

      daughters(0).reply(JobDone(messages(0).fileGroupId))
      daughters(0).expectNoMsg(Pause)

      motherWorker ! messages(2)

      daughters(0).expectMsg(messages(2))
    }

    "do nothing when StartClustering is received but all files have not been processed" in new MotherSetup {
      val numberOfUploads = 5

      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns (numberOfUploads - 1)

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)

      there was no(storage).submitDocumentSetCreationJob(any)

    }

    "submit a job when StartClustering is received and all files have been processed" in new MotherSetup {
      val numberOfUploads = 5
      val documentSetCreationJob = DocumentSetCreationJob(
        id = 1l,
        documentSetId = 10l,
        jobType = FileUpload,
        fileGroupId = Some(fileGroupId),
        state = Preparing)

      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId) returns Some(documentSetCreationJob)
      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads

      motherWorker ! StartClusteringCommand(fileGroupId, title, lang, stopWords)

      there was one(storage).submitDocumentSetCreationJob(documentSetCreationJob)
    }

    "do nothing when all files have been processed by StartClustering has not been received" in new MotherSetup {
      val numberOfUploads = 5

      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads
      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId) returns None

      motherWorker ! JobDone(fileGroupId)

      there was no(storage).submitDocumentSetCreationJob(any)
    }

    "submit a job when JobDone for the last processed file is received and StartClustering has been received" in new MotherSetup {
      val numberOfUploads = 5

      val documentSetCreationJob = DocumentSetCreationJob(
        id = 1l,
        documentSetId = 10l,
        jobType = FileUpload,
        fileGroupId = Some(fileGroupId),
        state = Preparing)

      storage.countFileUploads(fileGroupId) returns numberOfUploads
      storage.countProcessedFiles(fileGroupId) returns numberOfUploads
      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId) returns Some(documentSetCreationJob)

      motherWorker ! JobDone(fileGroupId)

      there was one(storage).submitDocumentSetCreationJob(documentSetCreationJob)
    }

    "requeue command failing daughter was processing" in new ActorSystemContext {
      val jobMonitor = TestProbe()
      val fileGroupId = 1l
      val command = ProcessFileCommand(fileGroupId, 2l)
      val motherWorker = TestActorRef(new MotherWorkerWithFailingDaughter(jobMonitor.ref))

      motherWorker ! command
      jobMonitor.expectMsg(JobDone(fileGroupId))
    }

    "remove queued commands when cancelling job" in new ActorSystemContext {
      val jobMonitorProbe = TestProbe()
      val daughters = Seq.fill(2)(system.actorOf(Props(new WaitingDaughter(jobMonitorProbe.ref))))
      val motherWorker = TestActorRef(new TestMotherWorker(daughters))
      val storage = motherWorker.underlyingActor.storage
      val runningFileGroupId = 1l
      val cancelledFileGroupId = 2l
      val documentSetId = 10l
      
      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns cancelledFileGroupId
      
      storage.countFileUploads(runningFileGroupId) returns 10
      storage.countProcessedFiles(runningFileGroupId) returns 5
      storage.findFileGroupWithDocumentSet(documentSetId) returns Some(fileGroup)

      val runningCommands = Seq.tabulate(4)(n => ProcessFileCommand(runningFileGroupId, n))
      val commandsToCancel = Seq.tabulate(5)(n => ProcessFileCommand(cancelledFileGroupId, n))

      val commandSequence = runningCommands.take(2) ++ commandsToCancel ++ runningCommands.drop(2)

      commandSequence.foreach(motherWorker ! _)
      motherWorker ! CancelUploadWithDocumentSetCommand(documentSetId)

      daughters(0) ! "finish running job"
      jobMonitorProbe.expectMsg(JobDone(runningFileGroupId))
      daughters(0) ! "finish job sent after cancellation"
      jobMonitorProbe.expectMsg(JobDone(runningFileGroupId))

    }

    "delete stored data when cancelling job" in new ActorSystemContext {
      val jobMonitorProbe = TestProbe()
      val daughters = Seq.fill(2)(system.actorOf(Props(new WaitingDaughter(jobMonitorProbe.ref))))
      val motherWorker = TestActorRef(new TestMotherWorker(daughters))
      val storage = motherWorker.underlyingActor.storage
      val documentSetId = 10l

      val cancelledFileGroupId = 2l

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns cancelledFileGroupId
      
      storage.findFileGroupWithDocumentSet(documentSetId) returns Some(fileGroup)

      motherWorker ! CancelUploadWithDocumentSetCommand(documentSetId)

      there was one(storage).deleteDocumentSetData(cancelledFileGroupId)
      there was one(storage).deleteFileGroupData(cancelledFileGroupId)
    }

    "wait for running jobs to complete before deleting stored data when cancelling job" in new ActorSystemContext {
      val jobMonitorProbe = TestProbe()
      val daughters = Seq.fill(2)(TestActorRef(new WaitingDaughter(jobMonitorProbe.ref)))
      val motherWorker = TestActorRef(new TestMotherWorker(daughters))
      val storage = motherWorker.underlyingActor.storage
      val documentSetId = 10l
      val cancelledFileGroupId = 2l

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns cancelledFileGroupId
      storage.findFileGroupWithDocumentSet(documentSetId) returns Some(fileGroup)
      
      val commandsToCancel = Seq.tabulate(5)(n => ProcessFileCommand(cancelledFileGroupId, n))

      commandsToCancel.foreach(motherWorker ! _)
      motherWorker ! CancelUploadWithDocumentSetCommand(documentSetId)
      
      there was one(storage).deleteDocumentSetData(cancelledFileGroupId)
      there was no(storage).deleteFileGroupData(cancelledFileGroupId)
      
      daughters(0) ! "Finish job"
      jobMonitorProbe.expectMsg(JobDone(cancelledFileGroupId))
      there was no(storage).deleteFileGroupData(cancelledFileGroupId)
      
      daughters(1) ! "Finish job"
      jobMonitorProbe.expectMsg(JobDone(cancelledFileGroupId))
      Thread.sleep(10) // Mockito does not work well in a multi-threaded environment. We need to find a better way.
      there was one(storage).deleteFileGroupData(cancelledFileGroupId)
    }

  }

}