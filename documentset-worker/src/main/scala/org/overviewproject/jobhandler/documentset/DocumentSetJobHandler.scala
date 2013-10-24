package org.overviewproject.jobhandler.documentset

import scala.concurrent.duration._
import akka.actor._
import org.overviewproject.jobhandler.{ ApolloMessageService, MessageQueueActor2, MessageService }
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.documentset.SearchHandlerProtocol.SearchDocumentSet
import org.overviewproject.searchindex.ElasticSearchComponents
import org.overviewproject.util.Configuration
import DocumentSetJobHandlerFSM._
import javax.jms._
import org.overviewproject.util.Logger


trait Command

/**
 * Messages the JobHandler can process
 */
object DocumentSetJobHandlerProtocol {
  // Internal messages that should really be private, but are 
  // public for easier testing. 
  case class SearchCommand(documentSetId: Long, query: String) extends Command
  case class DeleteCommand(documentSetId: Long) extends Command
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

class DocumentSetMessageHandler extends Actor with FSM[State, Data] {
  this: SearchComponent =>

  import DocumentSetJobHandlerProtocol._

  startWith(Ready, Working)

  when(Ready) {
    case Event(SearchCommand(documentSetId, query), _) => {
      Logger.info(s"Received search command $query")
      val searchHandler = context.actorOf(Props(actorCreator.produceSearchHandler))
      searchHandler ! SearchDocumentSet(documentSetId, query)
      goto(WaitingForCompletion)
    }
    case Event(DeleteCommand(documentSetId), _) => {
      val deleteHandler = context.actorOf(Props(actorCreator.produceDeleteHandler))
      deleteHandler ! DeleteDocumentSet(documentSetId)
      goto(WaitingForCompletion)
    }
  }

  when(WaitingForCompletion) {
    case Event(JobDone(documentSetId), _) => {
      context.parent ! MessageHandled
      goto(Ready)
    }
  }

  initialize
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

class DocumentSetMessageHandlerImpl extends DocumentSetMessageHandler with SearchComponentImpl {
  override val actorCreator = new ActorCreatorImpl
}


class DocumentSetJobHandler(messageService: MessageService) extends MessageQueueActor2[Command](messageService) {
  override def createMessageHandler: Props = Props[DocumentSetMessageHandlerImpl]
  override def convertMessage(message: String): Command = ConvertDocumentSetMessage(message)

} 


object DocumentSetJobHandler {
 private val messageService = 
   new ApolloMessageService(Configuration.messageQueue.getString("queue_name"), Session.CLIENT_ACKNOWLEDGE)
  
  def apply(): Props = Props(new DocumentSetJobHandler(messageService))
}
