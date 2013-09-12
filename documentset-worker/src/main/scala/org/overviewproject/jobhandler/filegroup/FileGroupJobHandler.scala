package org.overviewproject.jobhandler.filegroup

import scala.language.postfixOps
import akka.actor._
import scala.concurrent.{ future, Future }
import scala.concurrent.duration._
import FileGroupJobHandlerFSM._
import scala.util.{ Failure, Success }
import org.overviewproject.util.Logger
import org.overviewproject.jobhandler.filegroup.FileHandlerProtocol._
import scala.concurrent.Promise
import org.overviewproject.jobhandler.MessageServiceComponent
import org.overviewproject.jobhandler.MessageServiceComponentImpl



trait TextExtractorComponent {
  val actorCreator: ActorCreator
  
  trait ActorCreator {
    def produceTextExtractor: Actor
  }
}

object FileGroupJobHandlerProtocol {
  case object ListenForFileGroupJobs
  
  case class ConnectionFailure(e: Exception)
  case class ProcessFileCommand(documentSetId: Long, uploadedFileId: Long)
}

object FileGroupJobHandlerFSM {
  sealed trait State
  case object NotConnected extends State
  case object Ready extends State
  case object WaitingForCompletion extends State

  sealed trait Data
  case object Working extends Data
  case class ConnectionFailed(e: Throwable) extends Data
}

class FileGroupJobHandler extends Actor with FSM[State, Data] {
  this: MessageServiceComponent with TextExtractorComponent =>

  import FileGroupJobHandlerProtocol._
  
  private var currentJobCompletion: Option[Promise[Unit]] = None
  
  private val ReconnectionInterval = 1 seconds

  startWith(NotConnected, Working)

  when(NotConnected) {
    case Event(ListenForFileGroupJobs, _) => {
      val connectionStatus = messageService.createConnection(deliverMessage, handleConnectionFailure)
      connectionStatus match {
        case Success(_) => goto(Ready)
        case Failure(e) => {
          Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
          setTimer("retry", ListenForFileGroupJobs, ReconnectionInterval, repeat = false)
          stay using ConnectionFailed(e)
        }
      }
    }
    case Event(ConnectionFailure(e), _) => stay
  }

  when(Ready) {
    case Event(ConnectionFailure(e), _) => goto(NotConnected) using ConnectionFailed(e)
    case Event(ProcessFileCommand(documentSetId, uploadedFileId), _) => {
      val fileHandler = context.actorOf(Props(actorCreator.produceTextExtractor))
      fileHandler ! ExtractText(documentSetId, uploadedFileId)
      
      goto(WaitingForCompletion)
    }
  }
  
  when(WaitingForCompletion) {
    case Event(JobDone, _) => {
      currentJobCompletion.map(_.success())
      currentJobCompletion = None
      sender ! PoisonPill
      goto(Ready)		
    }
  }

  onTransition {
    case _ -> NotConnected => (nextStateData: @unchecked) match { // error if ConnectionFailed is not set
      case ConnectionFailed(e) => self ! ListenForFileGroupJobs
    }
  }

  initialize

  private def deliverMessage(message: String): Future[Unit] = {
    currentJobCompletion = Some(Promise[Unit])
    self ! ConvertFileGroupMessage(message)
    currentJobCompletion.get.future
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e)
  }
}

trait TextExtractorComponentImpl extends TextExtractorComponent {

  class ActorCreatorImpl extends ActorCreator {
    override def produceTextExtractor: Actor = {
      new FileHandlerImpl
    }
  }
 override val actorCreator = new ActorCreatorImpl

}

object FileGroupJobHandler {
  class FileGroupJobHandlerImpl extends FileGroupJobHandler with MessageServiceComponentImpl with TextExtractorComponentImpl {
    override val messageService = new MessageServiceImpl("/queue/file-group-commands")
  }
  
  def apply(): Props = Props[FileGroupJobHandlerImpl]
}