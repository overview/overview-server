package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.{Inject,Singleton}
import scala.concurrent.Future

import com.overviewdocs.messages.{DocumentSetCommands,DocumentSetReadCommands}
import com.overviewdocs.query.Query
import com.overviewdocs.searchindex.SearchResult
import modules.RemoteActorSystemModule

/** Queries a search index.
  */
@ImplementedBy(classOf[RemoteActorSearchBackend])
trait SearchBackend extends Backend {
  /** Rewrite a document to the search index, to incorporate changes.
    *
    * The document will be read from the database.
    */
  def refreshDocument(documentSetId: Long, documentId: Long): Future[Unit]

  /** Lists all document IDs that match the given query.
    *
    * The query may be well-formed but be inappropriate for the given document
    * (e.g., a boolean query on too many terms). In that case, the server will
    * respond with a Left.
    *
    * @param documentSetId DocumentSet ID.
    * @param query Parsed search query.
    */
  def search(documentSetId: Long, query: Query): Future[SearchResult]
}

/** Akka RemoteActor-backed search backend.
  */
@Singleton
class RemoteActorSearchBackend @Inject() (remoteActorSystemModule: RemoteActorSystemModule)
extends SearchBackend {
  import akka.actor.ActorRef
  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._

  private implicit val system = remoteActorSystemModule.remoteActorSystem
  private implicit val timeout = Timeout(30.seconds)
  private val workerActor = remoteActorSystemModule.workerActor

  override def search(documentSetId: Long, query: Query) = {
    workerActor.ask(DocumentSetReadCommands.Search(documentSetId, query)).mapTo[SearchResult]
  }

  override def refreshDocument(documentSetId: Long, documentId: Long) = {
    workerActor.tell(DocumentSetCommands.ReindexDocument(documentSetId, documentId), ActorRef.noSender)

    // We'll return before write completes. That's iffy, certainly, and the real
    // reason is architectural. But we can rationalize this:
    //
    // 1. On the worker, future reads/writes won't execute until this write
    //    happens. So from the user's perspective, things still appear serial.
    // 2. If the message gets lost, or if there's a race, the consequences are
    //    tiny.
    Future.successful(())
  }
}
