package org.overviewproject.jobhandler

import org.fusesource.stomp.jms.{ StompJmsConnectionFactory, StompJmsDestination }
import akka.actor._
import javax.jms._
import org.overviewproject.jobhandler.SearchHandlerProtocol.Search
import org.overviewproject.jobhandler.DocumentSearcherProtocol.Done

class ActualSearchHandler extends SearchHandler with SearchHandlerComponents {
  override val storage: Storage = new Storage
  override val actorCreator: ActorCreator = new ActorCreator
}

object JobHandlerProtocol {
  case object StartListening
  case class CommandMessage(message: TextMessage)
  case class SearchCommand(documentSetId: Long, query: String)
}

trait MessageServiceComponent {

  val messageService: MessageService

  class MessageService {
    private val connection: Connection = createConnection
    private val consumer: MessageConsumer = createConsumer

    def startListening(messageHandler: MessageListener): Unit = {
      consumer.setMessageListener(messageHandler)
      connection.start()
    }
    
    def complete(message: Message): Unit = {
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

trait SearchComponent {
  val actorCreator: ActorCreator
  
  class ActorCreator {
    def produceSearchHandler: Actor = new SearchHandler with SearchHandlerComponents {
      override val storage: Storage = new Storage
      override val actorCreator: ActorCreator = new ActorCreator()
    }
  }
}

object JobHandlerFSM {
  sealed trait State
  case object Idle extends State
  case object Listening extends State
  case object WaitingForCompletion extends State
  
  sealed trait Data
  case object NoMessageReceived extends Data
  case class MessageReceived(message: Message) extends Data
  
}

import JobHandlerFSM._

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
    def onMessage(message: Message): Unit = {}//convertMessage(message).map { cmd => self ! cmd }
  }

}

object JobHandler {
  class ActualJobHandler(requestQueue: ActorRef) extends JobHandler(requestQueue) 
    with MessageServiceComponent with SearchComponent {
    
    override val messageService = new MessageService
    override val actorCreator = new ActorCreator
  }
  
  def apply(requestQueue: ActorRef): Props = Props(new ActualJobHandler(requestQueue))
}
