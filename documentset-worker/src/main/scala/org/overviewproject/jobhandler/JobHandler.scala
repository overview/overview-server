package org.overviewproject.jobhandler

import scala.language.postfixOps
import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import org.overviewproject.jobhandler.DocumentSearcherProtocol.DocumentSearcherDone
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
 * Ready -> WaitingForCompletion: when a message has been received and sent of to a handler
 * WaitingForCompletion -> Ready: when the command handler is done
 */
object JobHandlerFSM {
  sealed trait State
  case object NotConnected extends State
  case object Ready extends State
  case object WaitingForCompletion extends State
  
  sealed trait Data
  case object NoData extends Data
}

/**
 * A component that listens for and responds to incoming messages.
 */
trait MessageServiceComponent {

  val messageService: MessageService

  trait MessageService {
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
  
  startWith(NotConnected, NoData)

  when (NotConnected) {
    case Event(StartListening, _) => {
      Logger.debug("Attempting to connect")
      val connectionStatus = messageService.createConnection(deliverMessage, handleConnectionFailure)
      connectionStatus match {
        case Success(_) => goto(Ready)
        case Failure(e) => {
          import context.dispatcher
          context.system.scheduler.scheduleOnce(1 seconds, self, StartListening)
          stay
        }
      }
    }
  }
  
  when(Ready) {
    case Event(SearchCommand(documentSetId, query), _) => {
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      searchHandler ! SearchDocumentSet(documentSetId, query, requestQueue)
      goto(WaitingForCompletion) 
    }
    case Event(ConnectionFailure(e), _) => {
      self ! StartListening
      goto(NotConnected)
    }
  }

  when(WaitingForCompletion) {
    case Event(JobDone, _) => {
      currentJobCompletion.map(_.success())
      goto(Ready) 
    }
    case Event(ConnectionFailure(e), _) => {
      self ! StartListening
      goto(NotConnected)
    }
    
  }

  initialize

  private def deliverMessage(message: String): Future[Unit] = {
    currentJobCompletion = Some(Promise[Unit])
    self ! ConvertMessage(message)
    currentJobCompletion.get.future
  }
  
  private def handleConnectionFailure(e: Exception): Unit = {
    Logger.error(s"Connection Failure: ${e.getMessage}")
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
    }
    
    private class MessageHandler(messageDelivery: String => Future[Unit]) extends MessageListener {
      override def onMessage(message: Message): Unit = {
        val jobComplete = messageDelivery(message.asInstanceOf[TextMessage].getText)
        Await.result(jobComplete, Duration.Inf)
        message.acknowledge
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
