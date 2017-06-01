package controllers.backend

import akka.util.Timeout
import com.google.inject.ImplementedBy
import com.redis.RedisClient
import com.redis.protocol.StringCommands
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.{InMemorySelection,Selection,SelectionRequest}
import models.pagination.{Page,PageInfo,PageRequest}
import modules.RedisModule

/** A store of Selections.
  *
  * A Selection is a list of document IDs. It is created via the
  * findDocumentIds() method, with a SelectionRequest. We persist
  * Selections so we can paginate over them. (We can't paginate using just the
  * query params, because findDocumentIds() doesn't necessarily return the same
  * list of document IDs on each call. See
  * https://www.pivotaltracker.com/story/show/92354552.)
  *
  * Think of this as a cache. If you findOrCreate() on a request that refers
  * back to a previously-cached selection, you'll get the cached selection
  * back.
  */
@ImplementedBy(classOf[RedisSelectionBackend])
trait SelectionBackend extends Backend {
  protected val documentBackend: DocumentBackend

  /** Converts a SelectionRequest to a new Selection.
    *
    * This involves calling findDocumentIds and caching the result.
    */
  def create(userEmail: String, request: SelectionRequest): Future[Selection]

  /** Returns an existing Selection, if it exists. */
  def find(documentSetId: Long, selectionId: UUID): Future[Option[Selection]]

  /** Finds an existing Selection, or returns a new one.
    *
    * This takes more inputs than simple find() or create(). It does this:
    *
    * 1. If maybeSelectionId exists, search for that Selection and return it.
    * 2. Otherwise, search for a Selection based on request.hash and return it.
    * 3. Otherwise, call create().
    */
  def findOrCreate(userEmail: String, request: SelectionRequest, maybeSelectionId: Option[UUID]): Future[Selection]

  /** Does the grunt work for the create() method. */
  protected def findDocumentIds(request: SelectionRequest): Future[Seq[Long]] = {
    documentBackend.indexIds(request)
  }
}

class NullSelectionBackend @Inject() (
  override val documentBackend: DocumentBackend
) extends SelectionBackend {

  override def create(userEmail: String, request: SelectionRequest) = {
    findDocumentIds(request).map(InMemorySelection(_))
  }

  override def findOrCreate(userEmail: String, request: SelectionRequest, maybeSelectionId: Option[UUID]) = {
    create(userEmail, request)
  }

  override def find(documentSetId: Long, selectionId: UUID) = Future.successful(None)
}

/** Stores Selections in Redis.
  *
  * We store the following keys:
  *
  * * `selection:[documentSetId]:by-user-hash:[email]:[hash of query params]`:
  *   A (String) Selection ID. The `documentSetId` is for consistency (and
  *   sharding); the `email` is so that two users viewing the same document set
  *   don't affect one another's sessions; and the `hash` is the part that
  *   ensures that two subsequent requests for the same query parameters can
  *   return the same Selection.
  * * `selection:[documentSetId]:by-id:[id]:document-ids`: A (String) byte
  *   array of 64-bit document IDs.
  *
  * Selections all expire. find() and findOrCreate() reset the expiry time.
  */
class RedisSelectionBackend @Inject() (
  override val documentBackend: DocumentBackend,
  val redisModule: RedisModule
) extends SelectionBackend {
  protected val redis: RedisClient = redisModule.client

  private[backend] val ExpiresInSeconds: Int = 60 * 60 // Used in unit tests, too
  private val SizeOfLong = 8

  private def requestHashKey(userEmail: String, request: SelectionRequest) = {
    s"selection:${request.documentSetId}:by-user-hash:${userEmail}:${request.hash}"
  }

  private def documentIdsKey(documentSetId: Long, selectionId: UUID) = {
    s"selection:${documentSetId}:by-id:${selectionId}:document-ids"
  }

  case class RedisSelection(val documentSetId: Long, override val id: UUID) extends Selection {
    private def throwMissingError = throw new Exception("document IDs disappeared even though we _just_ reset their expire time")

    private val key = documentIdsKey(documentSetId, id)

    override def getDocumentCount: Future[Int] = {
      redis
        .strlen(key)
        .map((n: Long) => (n / SizeOfLong).toInt)
    }

    private def getDocumentIdsArray(offset: Int, limit: Int): Future[Array[Long]] = {
      redis
        .getrange[Array[Byte]](key, offset * SizeOfLong, (offset + limit) * SizeOfLong - 1)
        .map(_.getOrElse(throwMissingError))
        .map { (bytes: Array[Byte]) =>
          val buf = ByteBuffer.wrap(bytes).asLongBuffer
          val longs = Array.fill(buf.capacity)(0L)
          buf.get(longs)
          longs
        }
    }

    override def getDocumentIds(page: PageRequest): Future[Page[Long]] = {
      for {
        total <- getDocumentCount
        longs <- getDocumentIdsArray(page.offset, page.limit)
      } yield Page(longs, PageInfo(page, total.toInt))
    }

    override def getAllDocumentIds: Future[Seq[Long]] = {
      getDocumentIdsArray(0, Int.MaxValue / SizeOfLong)
        .map(_.toSeq)
    }
  }

  // pudo's 2M documents led to:
  // play.api.UnexpectedException: Unexpected exception[AskTimeoutException: Ask timed out on [Actor[akka://RedisPlugin/user/redis-client-0#429941718]] after [1000 ms]]
  // ... and after that, every future request for documents failed.
  //
  // The delay comes from the actor not responding. The actor doesn't respond
  // because the CPU (just one, on production) is too busy to attend to it.
  // This isn't a legitimate exception; expanding timeout to 60s.
  private implicit val timeout: Timeout = Timeout(60, java.util.concurrent.TimeUnit.SECONDS)

  private def encodeDocumentIds(documentIds: Seq[Long]): Array[Byte] = {
    val buffer = ByteBuffer.allocate(documentIds.length * SizeOfLong)
    documentIds.foreach(buffer.putLong)
    buffer.array
  }

  private def findAndExpireByHash(userEmail: String, request: SelectionRequest): Future[Option[Selection]] = {
    val hashKey = requestHashKey(userEmail, request)
    redis
      .eval("""
        local selection_id = redis.call("GET", KEYS[1])
        if selection_id == false then
          return nil
        else
          redis.call("EXPIRE", KEYS[1], ARGV[2])
          local selection_key = "selection:" .. ARGV[1] .. ":by-id:" .. selection_id .. ":document-ids"
          if redis.call("EXPIRE", selection_key, ARGV[2]) == 0 then
            return nil
          else
            return selection_id
          end
        end
      """, List(hashKey), List(request.documentSetId.toString, ExpiresInSeconds.toString))
      .map(_ match {
        case List(id: String) => Some(RedisSelection(request.documentSetId, UUID.fromString(id)))
        case _ => None
      })
  }

  private def findAndExpireById(documentSetId: Long, selectionId: UUID): Future[Option[Selection]] = {
    val idKey = documentIdsKey(documentSetId, selectionId)
    for { isSet <- redis.expire(idKey, ExpiresInSeconds) }
    yield if (isSet) Some(RedisSelection(documentSetId, selectionId)) else None
  }

  override def create(userEmail: String, request: SelectionRequest) = {
    val selectionId = UUID.randomUUID()
    val byUserHashKey = requestHashKey(userEmail, request)
    val byIdKey = documentIdsKey(request.documentSetId, selectionId)

    for {
      documentIds <- findDocumentIds(request)
      _ <- redis.set(byUserHashKey, selectionId.toString, StringCommands.EX(ExpiresInSeconds))
      _ <- redis.set(byIdKey, encodeDocumentIds(documentIds), StringCommands.EX(ExpiresInSeconds))
    } yield RedisSelection(request.documentSetId, selectionId)
  }

  override def find(documentSetId: Long, selectionId: UUID) = {
    findAndExpireById(documentSetId, selectionId)
  }

  override def findOrCreate(userEmail: String, request: SelectionRequest, maybeSelectionId: Option[UUID]) = {
    val selection1: Future[Option[Selection]] = maybeSelectionId match {
      case Some(selectionId) => findAndExpireById(request.documentSetId, selectionId)
      case None => Future.successful(None)
    }

    val selection2: Future[Option[Selection]] = selection1
      .flatMap(_ match {
        case Some(selection) => Future.successful(Some(selection))
        case None => findAndExpireByHash(userEmail, request)
      })

    val selection3: Future[Selection] = selection2
      .flatMap(_ match {
        case Some(selection: Selection) => Future.successful(selection)
        case None => create(userEmail, request)
      })

    selection3
  }
}
