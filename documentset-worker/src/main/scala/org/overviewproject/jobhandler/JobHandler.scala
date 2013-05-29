package org.overviewproject.jobhandler

import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import org.overviewproject.jobhandler.DocumentSearcherProtocol.DocumentSearcherDone
import org.overviewproject.jobhandler.SearchHandlerProtocol.Search
import akka.actor._
import javax.jms._

import JobHandlerFSM._
import org.overviewproject.util.Configuration

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
}

/**
 * `JobHandler` goes through the following state transitions:
 * Ready -> WaitingForCompletion: when a message has been received and sent of to a handler
 * WaitingForCompletion -> Ready: when the command handler is done
 */
object JobHandlerFSM {
  sealed trait State
  case object Ready extends State
  case object WaitingForCompletion extends State

  sealed trait Data
  case object NoMessageReceived extends Data
  /** Keep track of the message received so it can be ACK/NACKed */
  case class MessageReceived(message: Message) extends Data

}

/**
 * A component that listens for and responds to incoming messages.
 */
trait MessageServiceComponent {

  val messageService: MessageService

  trait MessageService {
    /**
     *  wait for the next message to arrive from the queue. Blocks
     *  until a message is received.
     */
    def waitForMessage: TextMessage

    /** Call to indicate successful handling of the message */
    def complete(message: Message): Unit
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

  startWith(Ready, NoMessageReceived)

  when(Ready) {
    case Event(StartListening, NoMessageReceived) =>
      val message = messageService.waitForMessage
      self ! ConvertMessage(message.getText)
      stay using MessageReceived(message)
    case Event(SearchCommand(documentSetId, query), MessageReceived(message)) =>
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      searchHandler ! Search(documentSetId, query, requestQueue)
      goto(WaitingForCompletion) using MessageReceived(message)
  }

  when(WaitingForCompletion) {
    case Event(JobDone, MessageReceived(message)) =>
      messageService.complete(message)
      self ! StartListening
      goto(Ready) using NoMessageReceived
  }

  initialize

}

/**
 * Implementation of connecting to the message queue and receiving and responding to
 * messages.
 */
trait MessageServiceComponentImpl extends MessageServiceComponent {
  class MessageServiceImpl extends MessageService {
    private val BrokerUri: String = Configuration.messageQueue.brokerUri
    private val Username: String = Configuration.messageQueue.username
    private val Password: String = Configuration.messageQueue.password
    private val QueueName: String = Configuration.messageQueue.queueName

    private val connection: Connection = createConnection
    private val consumer: MessageConsumer = createConsumer

    override def waitForMessage: TextMessage = consumer.receive().asInstanceOf[TextMessage]

    override def complete(message: Message): Unit = message.acknowledge()

    private def createConnection: Connection = {
      val factory = new StompJmsConnectionFactory()
      factory.setBrokerURI(BrokerUri)
      val connection = factory.createConnection(Username, Password)
      connection.start()
      connection
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
