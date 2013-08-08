package org.overviewproject.jobhandler

import scala.language.postfixOps
import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import org.overviewproject.jobhandler.SearchHandlerProtocol.SearchDocumentSet
import akka.actor._
import javax.jms._
import JobHandlerFSM._
import org.overviewproject.util.Configuration
import scala.util.{ Failure, Success, Try }
import scala.annotation.tailrec
import scala.concurrent.Promise
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import org.overviewproject.util.Logger

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
  case class SearchCommand(documentSetId: Long, query: String)
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
 * A component that listens for and responds to incoming messages.
 */
trait MessageServiceComponent {

  val messageService: MessageService

  trait MessageService {
    /**
     *  Create a connection to the message queue.
     *  @returns `Success` if connection is  established, `Failure` otherwise
     *  @param messageDelivery will be called when a new message is received. The method
     *  should return a `Future` that will be completed when the job specified in the message
     *  has finished processing.
     *  @param failureHandler will be called if the connection fails.
     */
    def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit]
  }
}

/**
 * Component for creating a SearchHandler actor
 */
trait SearchComponent {
  val actorCreator: ActorCreator

  trait ActorCreator {
    def produceSearchHandler: Actor
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
      searchHandler ! SearchDocumentSet(documentSetId, query, requestQueue)
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
    self ! ConvertMessage(message)
    currentJobCompletion.get.future
  }

  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.info(s"Connection Failure: ${e.getMessage}")
    self ! ConnectionFailure(e)
  }

}

/**
 * Implementation of connecting to the message queue and receiving and responding to
 * messages.
 */
trait MessageServiceComponentImpl extends MessageServiceComponent {
  class MessageServiceImpl extends MessageService {
    private val ConnectionRetryPause = 2000
    private val MaxConnectionAttempts = 5

    private val BrokerUri: String = Configuration.messageQueue.brokerUri
    private val Username: String = Configuration.messageQueue.username
    private val Password: String = Configuration.messageQueue.password
    private val QueueName: String = Configuration.messageQueue.queueName

    private var connection: Connection = _
    private var consumer: MessageConsumer = _

    override def createConnection(messageDelivery: String => Future[Unit], failureHandler: Exception => Unit): Try[Unit] = Try {
      val factory = new StompJmsConnectionFactory()
      factory.setBrokerURI(BrokerUri)
      connection = factory.createConnection(Username, Password)
      connection.setExceptionListener(new FailureHandler(failureHandler))
      val messageHandler = new MessageHandler(messageDelivery)
      consumer = createConsumer
      consumer.setMessageListener(messageHandler)
      connection.start
      Logger.info("Connected to message broker")
    }

    private class MessageHandler(messageDelivery: String => Future[Unit]) extends MessageListener {

      // We need to wait for the job to complete, before returning
      // from call
      override def onMessage(message: Message): Unit = {
        val jobComplete = messageDelivery(message.asInstanceOf[TextMessage].getText)
        Await.result(jobComplete, Duration.Inf)
        jobComplete.value.map {
          case Success(_) => message.acknowledge
          case _ => // don't acknowledge. Connection is broken, so this probably doesn't matter
        }
      }
    }

    private class FailureHandler(handleFailure: Exception => Unit) extends ExceptionListener {
      override def onException(e: JMSException): Unit = handleFailure(e)
    }

    private def createConsumer: MessageConsumer = {
      val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
      val destination = new StompJmsDestination(QueueName)

      session.createConsumer(destination)
    }
  }
}

/** Create a SearchHandler */
trait SearchComponentImpl extends SearchComponent {
  class ActorCreatorImpl extends ActorCreator {
    override def produceSearchHandler: Actor = new SearchHandler with SearchHandlerComponentsImpl {
      override val storage: Storage = new StorageImpl
      override val actorCreator: ActorCreator = new ActorCreatorImpl
    }
  }
}

object JobHandler {
  class JobHandlerImpl(requestQueue: ActorRef) extends JobHandler(requestQueue)
      with MessageServiceComponentImpl with SearchComponentImpl {

    override val messageService = new MessageServiceImpl
    override val actorCreator = new ActorCreatorImpl
  }

  def apply(requestQueue: ActorRef): Props = Props(new JobHandlerImpl(requestQueue))
}
