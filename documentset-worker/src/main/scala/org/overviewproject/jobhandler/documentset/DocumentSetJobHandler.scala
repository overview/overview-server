package org.overviewproject.jobhandler.documentset

import scala.concurrent.duration._
import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol._
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.messagequeue.{ AcknowledgingMessageReceiver, MessageService }
import org.overviewproject.messagequeue.MessageHandlerProtocol._
import org.overviewproject.messagequeue.apollo.ApolloMessageService
import org.overviewproject.searchindex.ElasticSearchComponents
import org.overviewproject.util.Configuration
import org.overviewproject.util.Logger
import javax.jms._
import DocumentSetJobHandlerFSM._



trait Command

/**
 * Messages the JobHandler can process
 */
object DocumentSetJobHandlerProtocol {
  // Internal messages that should really be private, but are 
  // public for easier testing. 
  case class SearchCommand(documentSetId: Long, query: String) extends Command
  case class DeleteCommand(documentSetId: Long, waitForJobRemoval: Boolean) extends Command
  case class DeleteTreeJobCommand(documentSetId: Long) extends Command
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

/**
 * Component for creating a SearchHandler actor
 */
trait SearchComponent {
  val actorCreator: ActorCreator

  trait ActorCreator {
    def produceSearchHandler: Actor
    def produceDeleteHandler: Actor
  }
}

/**
 * The DocumentSetMessageHandler receives document set related commands
 * and forwards them to command specific actors. When the command actors 
 * are done, an acknowledgment is sent to the requesting parent actor.
 */
class DocumentSetMessageHandler extends Actor with FSM[State, Data] {
  this: SearchComponent =>

  import DocumentSetJobHandlerProtocol._

  override val supervisorStrategy =
    OneForOneStrategy(0, Duration.Inf) {
      case _: Exception => Stop
      case _: Throwable => Escalate
    }

  startWith(Ready, Working)

  when(Ready) {
    case Event(SearchCommand(documentSetId, query), _) => {
      Logger.info(s"Received Search($documentSetId, $query)")
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      context.watch(searchHandler)

      searchHandler ! SearchDocumentSet(documentSetId, query)
      goto(WaitingForCompletion)
    }
    case Event(DeleteCommand(documentSetId, waitForJobRemoval), _) => {
      Logger.info(s"Received Delete($documentSetId)")
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler))
      context.watch(deleteHandler)

      deleteHandler ! DeleteDocumentSet(documentSetId, waitForJobRemoval)
      goto(WaitingForCompletion)
    }
    case Event(DeleteTreeJobCommand(documentSetId), _) => {
      Logger.info(s"Received DeleteTreeJob($documentSetId)")
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler))
      context.watch(deleteHandler)
      
      deleteHandler ! DeleteReclusteringJob(documentSetId)
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
    override def produceSearchHandler: Actor = new SearchHandler with SearchIndexAndSearchStorage

    override def produceDeleteHandler: Actor = new DeleteHandler with ElasticSearchComponents {
      override val documentSetDeleter = DocumentSetDeleter()
      override val jobStatusChecker = JobStatusChecker()
    }
  }
}

class DocumentSetMessageHandlerImpl extends DocumentSetMessageHandler with SearchComponentImpl {
  override val actorCreator = new ActorCreatorImpl
}

class DocumentSetJobHandler(messageService: MessageService) extends AcknowledgingMessageReceiver[Command](messageService) {
  override def createMessageHandler: Props = Props[DocumentSetMessageHandlerImpl]
  override def convertMessage(message: String): Command = ConvertDocumentSetMessage(message)

}

object DocumentSetJobHandler {
  private val messageService =
    new ApolloMessageService(Configuration.messageQueue.getString("queue_name"), Session.CLIENT_ACKNOWLEDGE)

  def apply(): Props = Props(new DocumentSetJobHandler(messageService))
}
