package org.overviewproject.jobhandler

import org.fusesource.stomp.jms.{StompJmsConnectionFactory, StompJmsDestination}
import org.overviewproject.jobhandler.DocumentSearcherProtocol.Done
import org.overviewproject.jobhandler.SearchHandlerProtocol.Search

import akka.actor._
import javax.jms._



/**
 * Messages the JobHandler can process 
 */
object JobHandlerProtocol {
  /** Starts listening to the connection on the message queue */
  case object StartListening
  
  // Internal messages that should really be private, but are 
  // public for easier testing. 
  case class CommandMessage(message: TextMessage)
  case class SearchCommand(documentSetId: Long, query: String)
}


/**
 * `JobHandler` goes through the following state transitions:
 * Idle -> Listening: after startup and having received the StartListening message
 * Listening -> WaitingForCompletion: after a message has been received from the queue
 *   and a handler has been dispatched to deal with the command
 * WaitingForCompletion -> Listening: when the command handler is done
 */
object JobHandlerFSM {
  sealed trait State
  case object Idle extends State
  case object Listening extends State
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
     *  Only call `startListening` once. To stop listening, stop the Actor, which should
     *  disconnect the connection.
     */
    def startListening(messageHandler: MessageListener): Unit
    
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

import JobHandlerFSM._

/**
 * The `JobHandler` listens for messages on the queue, and then spawns an appropriate
 * handler for the incoming command. When the command handler sends a Done message,
 * the message is acknowledged.
 * To handle new types of command, expand `ConvertMessage` to generate new message type,
 * and add a new case to the `Listening` state to handle the new type.
 */
class JobHandler(requestQueue: ActorRef) extends Actor  with FSM[State, Data] {
  this: MessageServiceComponent with SearchComponent =>

  import JobHandlerProtocol._

  private val messageHandler = new MessageHandler
  
  startWith(Idle, NoMessageReceived)
  
  when(Idle) {
    case Event(StartListening, NoMessageReceived) => 
      messageService.startListening(messageHandler)
      goto(Listening) using NoMessageReceived
  }
  
  when (Listening) {
    case Event(CommandMessage(message), NoMessageReceived) => 
      self ! ConvertMessage(message.getText)
      stay using(MessageReceived(message))
    case Event(SearchCommand(documentSetId, query), MessageReceived(message)) =>
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      searchHandler ! Search(documentSetId, query, requestQueue)
      goto(WaitingForCompletion) using MessageReceived(message)
  }
  
  when (WaitingForCompletion) {
    case Event(Done, MessageReceived(message)) =>
      messageService.complete(message)
      goto(Listening) using NoMessageReceived
  }

  initialize
  
  class MessageHandler extends MessageListener {
    def onMessage(message: Message): Unit =  self ! CommandMessage(message.asInstanceOf[TextMessage])
  }

}


/**
 * Implementation of connecting to the message queue and receiving and responding to
 * messages.
 */
trait MessageServiceComponentImpl extends MessageServiceComponent {
  class MessageServiceImpl extends MessageService  {
    
    private val connection: Connection = createConnection
    private val consumer: MessageConsumer = createConsumer

    override def startListening(messageHandler: MessageListener): Unit = {
      consumer.setMessageListener(messageHandler)
      connection.start()
    }
    
    override def complete(message: Message): Unit = {
      message.acknowledge()
    }

    private def createConnection: Connection = {
      val factory = new StompJmsConnectionFactory()
      factory.setBrokerURI("tcp://localhost:61613")
      factory.createConnection("admin", "password")
    }

    private def createConsumer: MessageConsumer = {
      val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
      val destination = new StompJmsDestination("/queue/myqueue")

      session.createConsumer(destination)
    }
  }
}


/** Create a SearchHandler */
trait SearchComponentImpl extends SearchComponent {
  class ActorCreatorImpl extends ActorCreator {
    override def produceSearchHandler: Actor = new SearchHandler with SearchHandlerComponents {
      override val storage: Storage = new Storage
      override val actorCreator: ActorCreator = new ActorCreator()
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
