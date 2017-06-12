package controllers.backend

import akka.util.{ByteString,Timeout}
import com.google.inject.ImplementedBy
import redis.RedisClient
import java.nio.ByteBuffer
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.util.{Try,Success,Failure}

import com.overviewdocs.util.Logger
import models.{InMemorySelection,Selection,SelectionRequest,SelectionWarning}
import models.pagination.{Page,PageInfo,PageRequest}
import modules.RedisModule

/** A store of Selections.
  *
  * A Selection is a list of document IDs. It is first created via
  * DocumentSelectionBackend.createSelection(), but it may later be persisted.
  *
  * We persist Selections so we can paginate over them. (We can't paginate using
  * query params, because queries run at different times will give different
  * results -- and because querying is slower than reloading a Selection.)
  *
  * Think of this as a cache. If you findOrCreate() on a request that refers
  * back to a previously-cached selection, you'll get the cached selection
  * back.
  */
@ImplementedBy(classOf[RedisSelectionBackend])
trait SelectionBackend extends Backend {
  protected val documentSelectionBackend: DocumentSelectionBackend

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
}

class NullSelectionBackend @Inject() (
  override val documentSelectionBackend: DocumentSelectionBackend
) extends SelectionBackend {

  override def create(userEmail: String, request: SelectionRequest) = {
    documentSelectionBackend.createSelection(request)
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
  * * `selection:[documentSetId]:by-id:[id]:warnings`: A Serialized
  *   List[SelectionWarning].
  *
  * Selections all expire. find() and findOrCreate() reset the expiry time.
  */
class RedisSelectionBackend @Inject() (
  override val documentSelectionBackend: DocumentSelectionBackend,
  val redisModule: RedisModule
) extends SelectionBackend {
  protected val redis: RedisClient = redisModule.client

  private val logger: Logger = Logger.forClass(getClass)

  private[backend] val ExpiresInSeconds: Int = 60 * 60 // Used in unit tests, too
  private val SizeOfLong = 8

  private def requestHashKey(userEmail: String, request: SelectionRequest) = {
    s"selection:${request.documentSetId}:by-user-hash:${userEmail}:${request.hash}"
  }

  private def documentIdsKey(documentSetId: Long, selectionId: UUID) = {
    s"selection:${documentSetId}:by-id:${selectionId}:document-ids"
  }

  private def buildWarningsKey(documentSetId: Long, selectionId: UUID) = {
    s"selection:${documentSetId}:by-id:${selectionId}:warnings"
  }

  case class RedisSelection(val documentSetId: Long, override val id: UUID, override val warnings: List[SelectionWarning]) extends Selection {
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

    override def getAllDocumentIds: Future[Array[Long]] = {
      getDocumentIdsArray(0, Int.MaxValue / SizeOfLong)
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

  private def encodeDocumentIds(documentIds: Array[Long]): Array[Byte] = {
    val buffer = ByteBuffer.allocate(documentIds.length * SizeOfLong)
    documentIds.foreach(buffer.putLong)
    buffer.array
  }

  private def findAndExpireSelectionIdByHash(userEmail: String, request: SelectionRequest): Future[Option[UUID]] = {
    val hashKey = requestHashKey(userEmail, request)

    for {
      _ <- redis.expire(hashKey, ExpiresInSeconds)
      maybeUuidString: Option[String] <- redis.get[String](hashKey)
    } yield maybeUuidString.map(UUID.fromString)
  }

  private def findAndExpireSelectionById(documentSetId: Long, id: UUID): Future[Option[Selection]] = {
    val idsKey = documentIdsKey(documentSetId, id)
    val warningsKey = buildWarningsKey(documentSetId, id)

    for {
      idsExist <- redis.expire(idsKey, ExpiresInSeconds)
      _ <- redis.expire(warningsKey, ExpiresInSeconds)
      maybeWarningsString: Option[ByteString] <- redis.get(warningsKey)
    } yield (idsExist, maybeWarningsString) match {
      case (true, Some(warningsString)) => {
        parseWarnings(warningsString) match {
          case Success(warnings) => Some(RedisSelection(documentSetId, id, warnings))
          case Failure(_) => None
        }
      }
      case _ => None
    }
  }

  private def parseWarnings(byteString: ByteString): Try[List[SelectionWarning]] = {
    // TODO protobufs? Anything but java serializers.
    val stream = new java.io.ByteArrayInputStream(byteString.toArray)
    try {
      Success(new java.io.ObjectInputStream(stream).readObject().asInstanceOf[List[SelectionWarning]])
    } catch {
      case e: Exception => {
        // Normally, catch-all exceptions are bad. But we want the exact same
        // behavior for all potential exceptions:
        // * ClassNotFoundException
        // * InvalidClassException
        // * StreamCorruptedException
        // * OptionalDataException
        // * IOException
        // * ClassCastException
        //
        // ... which is to pretend Redis is corrupted
        logger.warn("Error parsing selection warnings from Redis: {}", e)
        Failure(e)
      }
    }
  }

  private def encodeWarnings(warnings: List[SelectionWarning]): ByteString = {
    val stream = new java.io.ByteArrayOutputStream
    new java.io.ObjectOutputStream(stream).writeObject(warnings)
    ByteString(stream.toByteArray)
  }

  private def writeSelection(userEmail: String, request: SelectionRequest, selection: InMemorySelection): Future[Unit] = {
    val byUserHashKey = requestHashKey(userEmail, request)
    val byIdKey = documentIdsKey(request.documentSetId, selection.id)
    val warningsKey = buildWarningsKey(request.documentSetId, selection.id)

    for {
      _ <- redis.setex(byUserHashKey, ExpiresInSeconds, selection.id.toString)
      _ <- redis.setex(byIdKey, ExpiresInSeconds, encodeDocumentIds(selection.documentIds))
      _ <- redis.setex(warningsKey, ExpiresInSeconds, encodeWarnings(selection.warnings))
    } yield ()
  }

  override def create(userEmail: String, request: SelectionRequest) = {
    for {
      selection <- documentSelectionBackend.createSelection(request)
      _ <- writeSelection(userEmail, request, selection)
    } yield selection // return InMemorySelection, not a RedisSelection -- it's faster
  }

  override def find(documentSetId: Long, selectionId: UUID) = {
    findAndExpireSelectionById(documentSetId, selectionId)
  }

  override def findOrCreate(userEmail: String, request: SelectionRequest, maybeSelectionId: Option[UUID]) = {
    for {
      // 1. Get selection ID if at all possible
      selectionId: Option[UUID] <- (maybeSelectionId
        .map(id => Future.successful(Some(id)))
        .getOrElse(findAndExpireSelectionIdByHash(userEmail, request)))

      // 2. Read Selection from Redis if we have an ID
      selectionById: Option[Selection] <- (selectionId
        .map(id => findAndExpireSelectionById(request.documentSetId, id))
        .getOrElse(Future.successful(None)))

      // 3. Return the selection, or create a new one if we couldn't find one
      selection: Selection <- (selectionById
        .map(s => Future.successful(s))
        .getOrElse(create(userEmail, request)))
    } yield selection
  }
}
