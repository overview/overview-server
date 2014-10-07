package controllers.backend

import akka.util.Timeout
import com.redis.protocol.StringCommands
import java.nio.ByteBuffer
import java.util.{Date,UUID}
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.{Selection,SelectionLike,SelectionRequest}
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
  * the scope of this backend. Call `show()` if you <em>must</em> use a cached
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
  def create(userEmail: String, request: SelectionRequest): Future[SelectionLike]

  /** Converts a SelectionRequest to a new or existing Selection.
    *
    * This calls DocumentIdFinder iff the selection does not exist.
    */
  def findOrCreate(userEmail: String, request: SelectionRequest): Future[SelectionLike]

  /** Does the grunt work for the create() method. */
  protected def findDocumentIds(request: SelectionRequest): Future[Seq[Long]]
}

trait NullSelectionBackend extends SelectionBackend {
  override def create(userEmail: String, request: SelectionRequest) = {
    findDocumentIds(request).map(Selection(request, _))
  }

  override def findOrCreate(userEmail: String, request: SelectionRequest) = {
    create(userEmail, request)
  }
}

trait RedisSelectionBackend extends SelectionBackend { self: RedisBackend =>
  val ExpiresInSeconds: Long = 60 * 60

  class RedisSelection(
    override val id: UUID,
    override val timestamp: Date,
    override val request: SelectionRequest
  ) extends SelectionLike {
    private val SizeOfLong = 8
    private def throwMissingError = throw new Exception("document IDs disappeared even though we _just_ reset their expire time")

    private val key = s"selection:${id.toString}:document-ids"

    private def getDocumentCount: Future[Int] = {
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
  }

  private implicit val timeout: Timeout = Timeout.longToTimeout(1000)

  private def requestHashKey(userEmail: String, request: SelectionRequest) = {
    s"selection:by-user-hash:${userEmail}:${request.hash}"
  }

  private def documentIdsKey(selection: Selection) = {
    s"selection:${selection.id}:document-ids"
  }

  override def create(userEmail: String, request: SelectionRequest) = {
    findDocumentIds(request)
      .map(Selection(request, _))
      .flatMap { selection: Selection =>
        val key = requestHashKey(userEmail, request)
        redis
          .set(key, selection.id.toString, StringCommands.EX(ExpiresInSeconds))
          .map((b) => selection)
      }
      .flatMap { selection: Selection =>
        val buffer = ByteBuffer.allocate(selection.documentIds.length * 8)
        selection.documentIds.foreach(buffer.putLong)
        val key = documentIdsKey(selection)

        redis
          .set(key, buffer.array, StringCommands.EX(ExpiresInSeconds))
          .map((b) => selection)
      }
  }

  /** Finds the selection, if it exists.
    *
    * Note: we don't store dates in Redis, so the new selection will have
    * the current date.
    */
  private def findAndExpire(userEmail: String, request: SelectionRequest): Future[Option[SelectionLike]] = {
    val hashKey = requestHashKey(userEmail, request)
    redis
      .eval("""
        local selection_id = redis.call("GET", KEYS[1])
        if selection_id == false then
          return nil
        else
          redis.call("EXPIRE", KEYS[1], ARGV[1])
          local selection_key = "selection:" .. selection_id .. ":document-ids"
          if redis.call("EXPIRE", selection_key, ARGV[1]) == 0 then
            return nil
          else
            return selection_id
          end
        end
      """, List(hashKey), List(ExpiresInSeconds.toString))
      .map(_ match {
        case List(id: String) => Some(new RedisSelection(UUID.fromString(id), new Date(), request))
        case _ => None
      })
  }

  override def findOrCreate(userEmail: String, request: SelectionRequest) = {
    findAndExpire(userEmail, request)
      .flatMap(_ match {
        case Some(ret: SelectionLike) => Future.successful(ret)
        case None => create(userEmail, request)
      })
  }
}

object SelectionBackend extends RedisBackend with RedisSelectionBackend {
  override def findDocumentIds(request: SelectionRequest) = DocumentBackend.indexIds(request)
}
