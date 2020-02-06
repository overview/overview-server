package controllers.backend

import akka.actor.{Actor,ActorRef,Props,Status}
import akka.stream.{CompletionStrategy,OverflowStrategy,QueueOfferResult}
import akka.stream.scaladsl.{Keep,Sink,Source,SourceQueueWithComplete}
import com.google.inject.ImplementedBy
import javax.inject.{Inject,Singleton}
import scala.concurrent.{Future,Promise}
import scala.concurrent.duration.FiniteDuration
import scala.util.{Success,Failure}

import com.overviewdocs.database.Database
import com.overviewdocs.messages.{DocumentSetCommands,Progress}
import com.overviewdocs.models.DocumentIdList
import com.overviewdocs.models.tables.DocumentIdLists
import modules.RemoteActorSystemModule

@ImplementedBy(classOf[DbAkkaDocumentIdListBackend])
trait DocumentIdListBackend {
  /** Returns a DocumentIdList for the given DocumentSet+field.
    *
    * Be sure to call (and wait for the result of) createIfMissing() before
    * calling this method. Otherwise, its result may be None.
    *
    * Returns None if the DocumentIdList does not exist.
    */
  def show(documentSetId: Int, fieldName: String): Future[Option[DocumentIdList]]

  /** Checks if the DocumentIdList exists in the database; if it doesn't, starts
    * sorting documents and emits progress events until done.
    *
    * Call this before calling `show()`. However, be aware of a race: even after
    * createIfMissing() returns, it's possible for the DocumentIdList to
    * be deleted before a subsequent call to show() -- in which case show() will
    * return None. If the caller is going to createIfMissing() + show() in a
    * loop, be careful that the DocumentSet wasn't deleted: that's another
    * reason for show() to return None.
    *
    * emits: zero or more SortProgressEvent(f) events.
    * completes when: a DocumentIdList has been written to the database, or the
    *                 DocumentSet has been deleted, or the field is not a valid
    *                 MetadataField for the document set, or the sort has been
    *                 interrupted (e.g., because a user edited the field being
    *                 sorted).
    * fails when: there's a timeout (may be network error), or a programmer
    *             error.
    */
  def createIfMissing(documentSetId: Int, fieldName: String): Source[Progress.Sorting, akka.NotUsed]

  /** Checks if the DocumentIdList exists in the database; if it doesn't, starts
    * sorting documents and emits progress events until done, then resolves to
    * the DocumentIdList.
    *
    * Be aware of races. This process 1) looks for a cached result; 2) sorts;
    * and 3) looks for a cached result again. The cached result from step 2 can
    * disappear before step 3 happens, giving a result of None.
    *
    * emits: zero or more SortProgressEvent(f) events.
    * completes with: a DocumentIdList, or None.
    * fails when: there's a timeout (may be network error), or a programmer
    *             error.
    */
  def showOrCreate(documentSetId: Int, fieldName: String): Source[Progress.Sorting, Future[Option[DocumentIdList]]]
}

object DocumentIdListBackend {
  case class SortProgressEvent(progress: Double)
}

@Singleton
class DbAkkaDocumentIdListBackend @Inject() (
  val database: Database,
  remoteActorSystemModule: RemoteActorSystemModule
) extends DocumentIdListBackend {
  private implicit val system = remoteActorSystemModule.actorSystem
  import system.dispatcher
  private implicit val timeout = remoteActorSystemModule.defaultTimeout
  private val messageBroker = remoteActorSystemModule.messageBroker
  import database.api._

  private[backend] var maxNProgressEventsInBuffer: Int = 1
  private[backend] var idleTimeout: FiniteDuration = timeout.duration

  lazy val showCompiled = Compiled { (documentSetId: Rep[Int], fieldName: Rep[String]) =>
    DocumentIdLists
      .filter(_.documentSetId === documentSetId)
      .filter(_.fieldName === fieldName)
  }

  lazy val existsCompiled = Compiled { (documentSetId: Rep[Int], fieldName: Rep[String]) =>
    DocumentIdLists
      .filter(_.documentSetId === documentSetId)
      .filter(_.fieldName === fieldName)
      .map(_ => 1)
  }

  override def show(documentSetId: Int, fieldName: String) = {
    database.option(showCompiled(documentSetId, fieldName))
  }

  override def createIfMissing(documentSetId: Int, fieldName: String) = {
    val futureSource = database.option(existsCompiled(documentSetId, fieldName)).map(_ match {
      case None => create(documentSetId, fieldName)
      case Some(_) => Source.empty
    })

    Source.futureSource(futureSource).mapMaterializedValue(_ => akka.NotUsed)
  }

  private def create(documentSetId: Int, fieldName: String): Source[Progress.Sorting, akka.NotUsed] = {
    // This queue will be consumed by the web browser. The user only cares
    // about the most recent value (which is closest to the current progress),
    // so we use the dropBuffer strategy.
    val matchComplete: PartialFunction[Any, CompletionStrategy] = { case Status.Success(_: Unit) => CompletionStrategy.draining }
    val matchFailure: PartialFunction[Any, Throwable] = { case Status.Failure(ex: Throwable) => ex }
    val actorSource = Source.actorRef[Progress.SortProgress](
      matchComplete,
      matchFailure,
      maxNProgressEventsInBuffer,
      OverflowStrategy.dropBuffer
    )

    // When this Source is materialized (connected to a Sink and run), our
    // actorRefPromise will resolve. That's when we'll ask the MessageBroker to
    // begin the sort and send progress events to our ActorSource.
    val actorRefPromise = Promise[ActorRef]()
    for {
      actorRef <- actorRefPromise.future
    } yield {
      messageBroker.tell(DocumentSetCommands.SortField(documentSetId, fieldName), actorRef)
    }

    // Monitor the messages from the message broker. We want the Source actor to
    // finish when we receive a Progress.SortDone event. (See Source.actorRef
    // docs.)
    val finishSink = Sink.foreach[Progress.SortProgress](_ match {
      case Progress.SortDone => {
        for {
          actorRef <- actorRefPromise.future
        } yield {
          actorRef.tell(Status.Success(()), actorRef)
        }
      }
      case _ =>
    })

    actorSource
      .mapMaterializedValue { actorRef => actorRefPromise.success(actorRef); akka.NotUsed }
      .idleTimeout(idleTimeout)
      .alsoTo(finishSink)
      // Only pass through Progress.Sorting events.
      .collect { case progress: Progress.Sorting => progress }
  }

  override def showOrCreate(documentSetId: Int, fieldName: String): Source[Progress.Sorting, Future[Option[DocumentIdList]]] = {
    val futureSource = show(documentSetId, fieldName).map(_ match {
      case x: Some[DocumentIdList] => Source.empty.mapMaterializedValue(_ => Future.successful(x))
      case None => {
        createIfMissing(documentSetId, fieldName)
          .watchTermination()(Keep.right)
          .mapMaterializedValue { f: Future[akka.Done] => f.flatMap(_ => show(documentSetId, fieldName)) }
      }
    })
    Source.futureSource(futureSource)
      .mapMaterializedValue { f: Future[Future[Option[DocumentIdList]]] => f.flatMap(x => x) }
  }
}
