package controllers.backend

import com.google.inject.ImplementedBy
import java.time.Instant
import javax.inject.{Inject,Singleton}
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.messages.{DocumentSetCommands,DocumentSetReadCommands}
import com.overviewdocs.models.DocumentSetReindexJob
import com.overviewdocs.models.tables.DocumentSetReindexJobs
import com.overviewdocs.query.{Query=>IndexQuery}
import com.overviewdocs.searchindex.{SearchResult,SearchWarning}
import com.overviewdocs.util.Logger
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
  def search(documentSetId: Long, query: IndexQuery): Future[SearchResult]
}

/** Akka RemoteActor-backed search backend.
  */
@Singleton
class RemoteActorSearchBackend @Inject() (
  val database: Database,
  remoteActorSystemModule: RemoteActorSystemModule
) extends SearchBackend {
  import akka.actor.ActorRef
  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._
  private val logger = Logger.forClass(getClass)

  private implicit val system = remoteActorSystemModule.actorSystem
  import system.dispatcher
  private implicit val timeout = Timeout(30.seconds)
  private val messageBroker = remoteActorSystemModule.messageBroker

  import database.api._
  lazy val updateReindexJobCompiled = Compiled { documentSetId: Rep[Long] =>
    DocumentSetReindexJobs
      .filter(_.documentSetId === documentSetId)
      .map(_.lastRequestedAt)
  }
  lazy val inserter = (DocumentSetReindexJobs.map(t => (t.documentSetId, t.lastRequestedAt, t.startedAt, t.progress)) returning DocumentSetReindexJobs)

  private def startReindexing(documentSetId: Long): Future[Unit] = {
    val attributes = DocumentSetReindexJob.CreateAttributes(documentSetId)
    val tuple = (attributes.documentSetId, attributes.lastRequestedAt, attributes.startedAt, attributes.progress)
    for {
      job <- database.run(inserter.+=(tuple))
    } yield {
      messageBroker.tell(DocumentSetCommands.Reindex(job), ActorRef.noSender)
    }
  }

  private def ensureReindexing(documentSetId: Long): Future[Unit] = {
    logger.info(s"Document Set ${documentSetId} gave SearchWarning.IndexDoesNotExist; queueing reindex")
    database.run(updateReindexJobCompiled(documentSetId).update(Instant.now))
      .flatMap(_ match {
        case 0 => startReindexing(documentSetId)
        case _ => Future.successful(()) // it's already in the database, so
                                        // assume the worker knows about it
      })
  }

  override def search(documentSetId: Long, query: IndexQuery) = {
    messageBroker.ask(DocumentSetReadCommands.Search(documentSetId, query)).mapTo[SearchResult]
      .flatMap(result => result match {
        case SearchResult(_, List(SearchWarning.IndexDoesNotExist)) => {
          // If searchindex warns us that the index does not exist, that's
          // probably because we're in the midst of an upgrade. Make sure
          // there's a DocumentSetReindexJob in the database, notify the worker
          // about it, and then return the result (empty document set with
          // warning) for the website to display.
          ensureReindexing(documentSetId).map(_ => result)
        }
        case _ => Future.successful(result)
      })
  }

  override def refreshDocument(documentSetId: Long, documentId: Long) = {
    messageBroker.tell(DocumentSetCommands.ReindexDocument(documentSetId, documentId), ActorRef.noSender)

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
