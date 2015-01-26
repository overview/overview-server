package org.overviewproject.jobhandler.documentset

import scala.concurrent.duration._
import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol._
import org.overviewproject.messagequeue.{ AcknowledgingMessageReceiver, MessageService }
import org.overviewproject.messagequeue.MessageHandlerProtocol._
import org.overviewproject.messagequeue.apollo.ApolloMessageService
import org.overviewproject.searchindex.ElasticSearchClient
import org.overviewproject.util.Configuration
import org.overviewproject.util.Logger
import javax.jms._
import DocumentSetJobHandlerFSM._
import org.overviewproject.database.DocumentSetCreationJobDeleter



trait Command

/**
 * Messages the JobHandler can process
 */
object DocumentSetJobHandlerProtocol {
  // Internal messages that should really be private, but are 
  // public for easier testing. 
  case class DeleteCommand(documentSetId: Long, waitForJobRemoval: Boolean) extends Command
  case class DeleteTreeJobCommand(jobId: Long) extends Command
}

/**
 * `JobHandler` goes through the following state transitions:
 * NotConnected -> Ready: when StartListening has been received and a connection has been established
 * Ready -> WaitingForCompletion: when a message has been received and sent of to a handler
 * WaitingForCompletion -> Ready: when the command handler is done
 * <all states> -> NotConnected: when connection fails
 */
object DocumentSetJobHandlerFSM {
  sealed trait State
  case object Ready extends State
  case object WaitingForCompletion extends State

  // No data is kept
  sealed trait Data
  case object Working extends Data
}

/** Component for creating a SearchHandler actor
  */
trait SearchComponent {
  
  val actorCreator: ActorCreator

  trait ActorCreator {
    def produceDeleteHandler(fileGroupRemovalQueuePath: String): Actor
  }
}

/**
 * The DocumentSetMessageHandler receives document set related commands
 * and forwards them to command specific actors. When the command actors 
 * are done, an acknowledgment is sent to the requesting parent actor.
 */
class DocumentSetMessageHandler(fileRemovalQueuePath: String) extends Actor with FSM[State, Data] {
  this: SearchComponent =>

  import DocumentSetJobHandlerProtocol._

  override val supervisorStrategy =
    OneForOneStrategy(0, Duration.Inf) {
      case _: Exception => Stop
      case _: Throwable => Escalate
    }

  startWith(Ready, Working)

  when(Ready) {
    case Event(DeleteCommand(documentSetId, waitForJobRemoval), _) => {
      Logger.info(s"Received Delete($documentSetId)")
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler(fileRemovalQueuePath)))
      context.watch(deleteHandler)

      deleteHandler ! DeleteDocumentSet(documentSetId, waitForJobRemoval)
      goto(WaitingForCompletion)
    }
    case Event(DeleteTreeJobCommand(jobId), _) => {
      Logger.info(s"Received DeleteTreeJob($jobId)")
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler(fileRemovalQueuePath)))
      context.watch(deleteHandler)
      
      deleteHandler ! DeleteReclusteringJob(jobId)
      goto(WaitingForCompletion)
    }
  }

  when(WaitingForCompletion) {
    case Event(JobDone(documentSetId), _) => {
      Logger.info(s"DocumentSetJobHandler completed job $documentSetId")
      context.unwatch(sender)
      context.parent ! MessageHandled
      goto(Ready)
    }
    case Event(Terminated(a), _) => {
      Logger.info(s"DocumentSetJobHandler handler terminated")
      context.parent ! MessageHandled
      goto(Ready)
    }
  }

  initialize
}

/** Create a SearchHandler */
trait SearchComponentImpl extends SearchComponent {
  class ActorCreatorImpl extends ActorCreator {
    override def produceDeleteHandler(pathToFileRemovalQueue: String): Actor = new DeleteHandler {
      override val searchIndexClient = ElasticSearchClient.client
      override val documentSetDeleter = DocumentSetDeleter()
      override val jobDeleter = DocumentSetCreationJobDeleter()
      override val jobStatusChecker = JobStatusChecker()
      
      override protected val fileRemovalQueuePath: String = pathToFileRemovalQueue
    }
  }
}

class DocumentSetMessageHandlerImpl(fileRemovalQueuePath: String) extends
  DocumentSetMessageHandler(fileRemovalQueuePath) with SearchComponentImpl {
  override val actorCreator = new ActorCreatorImpl
}

class DocumentSetJobHandler(messageService: MessageService, fileRemovalQueuePath: String) extends AcknowledgingMessageReceiver[Command](messageService) {
  override def createMessageHandler: Props = Props(new DocumentSetMessageHandlerImpl(fileRemovalQueuePath))
  override def convertMessage(message: String): Command = ConvertDocumentSetMessage(message)
  


}

object DocumentSetJobHandler {
  private val messageService =
    new ApolloMessageService(Configuration.messageQueue.getString("queue_name"), Session.CLIENT_ACKNOWLEDGE)

  def apply(fileRemovalQueuePath: String): Props = 
    Props(new DocumentSetJobHandler(messageService, fileRemovalQueuePath))
}
