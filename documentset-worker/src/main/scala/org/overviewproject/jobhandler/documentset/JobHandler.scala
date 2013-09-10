package org.overviewproject.jobhandler.documentset

import javax.jms._
import scala.concurrent.{Promise, Future, Await}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import akka.actor._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.searchindex.ElasticSearchComponents
import JobHandlerFSM._
import org.overviewproject.jobhandler.MessageServiceComponent
import org.overviewproject.util.{ Configuration, Logger }
import org.overviewproject.jobhandler.MessageServiceComponentImpl
import org.overviewproject.jobhandler.ConvertDocumentSetMessage

trait Command

/**
 * Messages the JobHandler can process
 */
object JobHandlerProtocol {
  /** Start listening to the connection on the message queue */
  case object StartListening
  case object JobDone

  // Internal messages that should really be private, but are 
  // public for easier testing. 
  case class CommandMessage(message: TextMessage)
  case class SearchCommand(documentSetId: Long, query: String) extends Command
  case class DeleteCommand(documentSetId: Long) extends Command
  case class ConnectionFailure(e: Exception)
}

/**
 * `JobHandler` goes through the following state transitions:
 * NotConnected -> Ready: when StartListening has been received and a connection has been established
 * Ready -> WaitingForCompletion: when a message has been received and sent of to a handler
 * WaitingForCompletion -> Ready: when the command handler is done
 * <all states> -> NotConnected: when connection fails
 */
object JobHandlerFSM {
  sealed trait State
  case object NotConnected extends State
  case object Ready extends State
  case object WaitingForCompletion extends State

  // No data is kept
  sealed trait Data
  case object Working extends Data
  case class ConnectionFailed(e: Throwable) extends Data
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
 * The `JobHandler` listens for messages on the queue, and then spawns an appropriate
 * handler for the incoming command. When the command handler sends a Done message,
 * the message is acknowledged.
 * To handle new types of command, expand `ConvertMessage` to generate new message type,
 * and add a new case to the `Listening` state to handle the new type.
 */
class JobHandler(requestQueue: ActorRef) extends Actor with FSM[State, Data] {
  this: MessageServiceComponent with SearchComponent =>

  import JobHandlerProtocol._

  private var currentJobCompletion: Option[Promise[Unit]] = None

  // Time between reconnection attempts
  private val ReconnectionInterval = 1 seconds

  startWith(NotConnected, Working)

  when(NotConnected) {
    case Event(StartListening, _) => {
      val connectionStatus = messageService.createConnection(deliverMessage, handleConnectionFailure)
      connectionStatus match {
        case Success(_) => goto(Ready)
        case Failure(e) => {
          Logger.info(s"Connection to Message Broker Failed: ${e.getMessage}", e)
          setTimer("retry", StartListening, ReconnectionInterval, repeat = false)
          stay using ConnectionFailed(e)
        }

      }
    }
    case Event(JobDone, _) => stay
    case Event(ConnectionFailure, _) => stay // For some reason, and extra event is generated when job is in progress
  }

  when(Ready) {
    case Event(SearchCommand(documentSetId, query), _) => {
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      searchHandler ! SearchDocumentSet(documentSetId, query)
      goto(WaitingForCompletion)
    }
    case Event(DeleteCommand(documentSetId), _) => {
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler))
      deleteHandler ! DeleteDocumentSet(documentSetId)
      goto(WaitingForCompletion)
    }
    case Event(ConnectionFailure(e), _) => goto(NotConnected) using ConnectionFailed(e)
  }

  when(WaitingForCompletion) {
    case Event(JobDone, _) => {
      currentJobCompletion.map(_.success())
      currentJobCompletion = None
      goto(Ready)
    }
    case Event(ConnectionFailure(e), _) => {
      Logger.error(s"Connection Failure: ${e.getMessage}", e)
      goto(NotConnected) using ConnectionFailed(e)
    }
  }

  onTransition {
    case _ -> NotConnected => (nextStateData: @unchecked) match { // error if ConnectionFailed is not set
      case ConnectionFailed(e) => {
        currentJobCompletion.map { f =>
          f.failure(e)
          currentJobCompletion = None
        }
        self ! StartListening
      }
    }
  }

  initialize

  private def deliverMessage(message: String): Future[Unit] = {
    currentJobCompletion = Some(Promise[Unit])
    self ! ConvertDocumentSetMessage(message)
    currentJobCompletion.get.future
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e)
  }

}


/** Create a SearchHandler */
trait SearchComponentImpl extends SearchComponent {
  class ActorCreatorImpl extends ActorCreator {
    override def produceSearchHandler: Actor = new SearchHandler with SearchHandlerComponentsImpl {
      override val storage: Storage = new StorageImpl
      override val actorCreator: ActorCreator = new ActorCreatorImpl
    }
    
    override def produceDeleteHandler: Actor = new DeleteHandler with ElasticSearchComponents {}
  }
}

object JobHandler {
  class JobHandlerImpl(requestQueue: ActorRef) extends JobHandler(requestQueue)
      with MessageServiceComponentImpl with SearchComponentImpl {

    override val messageService = new MessageServiceImpl(Configuration.messageQueue.queueName)
    override val actorCreator = new ActorCreatorImpl

  }

  def apply(requestQueue: ActorRef): Props = Props(new JobHandlerImpl(requestQueue))
}
