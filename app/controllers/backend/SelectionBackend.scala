package controllers.backend

import akka.util.Timeout
import com.redis.protocol.StringCommands
import java.nio.ByteBuffer
import java.util.{Date,UUID}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.{InMemorySelection,Selection,SelectionRequest}
import models.pagination.{Page,PageInfo,PageRequest}

/** A store of Selections.
  *
  * A selection maps a SelectionRequest to a Seq[Long] of Document IDs at a
  * given point in time. We need selections to stick around a bit so we can
  * paginate over them, even when time moves forward.
  *
  * Think of this as a cache. If you showOrCreate() on a request that refers
  * back to a previously-cached selection, you'll get the cached selection
  * back.
  *
  * How do we know the caller wants the cached selection? Well, that's outside
  * the scope of this backend. Call `find()` if you <em>must</em> use a cached
  * Selection, `create()` if you <em>must</em> build a new Selection (i.e., you
  * want your search results freshened) or `showOrCreate()` if you want to use
  * an existing one if it's present or create a new one otherwise. If you want
  * more complicated logic, figure it out yourself, using `show()` and
  * `create()` as building blocks.
  */
trait SelectionBackend extends Backend {
  /** Converts a SelectionRequest to a new Selection.
    *
    * This involves calling the DocumentIdFinder.
    */
  def create(userEmail: String, request: SelectionRequest): Future[Selection]

  /** Converts a SelectionRequest to a new or existing Selection.
    *
    * This calls DocumentIdFinder iff the selection does not exist.
    */
  def findOrCreate(userEmail: String, request: SelectionRequest): Future[Selection]

  /** Does the grunt work for the create() method. */
  protected def findDocumentIds(request: SelectionRequest): Future[Seq[Long]]
}

trait NullSelectionBackend extends SelectionBackend {
  override def create(userEmail: String, request: SelectionRequest) = {
    findDocumentIds(request).map(InMemorySelection(_))
  }

  override def findOrCreate(userEmail: String, request: SelectionRequest) = {
    create(userEmail, request)
  }
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
  */
trait RedisSelectionBackend extends SelectionBackend { self: RedisBackend =>
  private[backend] val ExpiresInSeconds: Long = 60 * 60 // Used in unit tests, too
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

  private implicit val timeout: Timeout = Timeout.longToTimeout(1000)

  private def encodeDocumentIds(documentIds: Seq[Long]): Array[Byte] = {
    val buffer = ByteBuffer.allocate(documentIds.length * SizeOfLong)
    documentIds.foreach(buffer.putLong)
    buffer.array
  }

  /** Creates a new Selection, always. */
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

  private def findAndExpire(userEmail: String, request: SelectionRequest): Future[Option[Selection]] = {
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

  /** Finds the selection, if there is one for the given request; otherwise,
    * creates a new one.
    */
  override def findOrCreate(userEmail: String, request: SelectionRequest) = {
    findAndExpire(userEmail, request)
      .flatMap(_ match {
        case Some(ret: Selection) => Future.successful(ret)
        case None => create(userEmail, request)
      })
  }
}

object SelectionBackend extends RedisBackend with RedisSelectionBackend {
  override def findDocumentIds(request: SelectionRequest) = DocumentBackend.indexIds(request)
}
