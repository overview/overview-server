package controllers.util

import scala.util.control.Exception._
import scala.util.{ Failure, Success, Try }
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.mvc.Results._
import play.api.http.HeaderNames._
import org.overviewproject.util.ContentDisposition
import play.api.mvc.Result
import java.util.UUID
import models.orm.finders.FileGroupFinder
import org.overviewproject.postgres.LO
import models.orm.stores.GroupedFileUploadStore
import models.OverviewDatabase
import models.orm.finders.GroupedFileUploadFinder
import play.api.Logger

trait MassUploadFileIteratee {
  val DefaultBufferSize = 1024 * 1024

  val storage: Storage

  def apply(userEmail: String, request: RequestHeader, guid: UUID, bufferSize: Int = DefaultBufferSize): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] = {
    val fileGroup = storage.findCurrentFileGroup(userEmail)
      .getOrElse(storage.createFileGroup(userEmail))

    val validUploadStart = RequestInformation.fromRequest(request) match {
      case Some(info) => {
        val initialUpload = storage.findUpload(fileGroup.id, guid)
          .getOrElse(storage.createUpload(fileGroup.id, info.contentType, info.filename, guid, info.total))
        if (info.start > initialUpload.uploadedSize) {
          Left(BadRequest("Trying to resume upload past the last known byte"))
        } else {
          Right(initialUpload.copy(uploadedSize = info.start))
        }
      }
      case None => {
        Logger.error(s"Failed to parse upload request headers ${request.headers}")
        Left(BadRequest("Request did not specify Content-Range or Content-Length"))
      }
    }

    var buffer = Array[Byte]()

    Iteratee.fold[Array[Byte], Either[Result, GroupedFileUpload]](validUploadStart) { (upload, data) =>
      buffer ++= data
      if (buffer.size >= bufferSize) {
        val update = flushBuffer(upload, buffer)
        buffer = Array[Byte]()
        update
      } else upload
    } map { output =>
      if (buffer.size > 0) flushBuffer(output, buffer)
      else output
    }
  }

  trait Storage {
    def createFileGroup(userEmail: String): FileGroup
    def findCurrentFileGroup(userEmail: String): Option[FileGroup]
    def createUpload(fileGroupId: Long, contentType: String, filename: String, guid: UUID, size: Long): GroupedFileUpload
    def findUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload]
    def appendData(upload: GroupedFileUpload, data: Iterable[Byte]): GroupedFileUpload
  }

  private def flushBuffer(upload: Either[Result, GroupedFileUpload], buffer: Array[Byte]): Either[Result, GroupedFileUpload] =
    for {
      u <- upload.right
      update <- attemptAppend(u, buffer).right
    } yield update

  private def attemptAppend(upload: GroupedFileUpload, buffer: Array[Byte]): Either[Result, GroupedFileUpload] = {
    val appendResult = allCatch either storage.appendData(upload, buffer)

    for (error <- appendResult.left) yield {
      Logger.error(s"Failed to append buffer during upload", error)
      InternalServerError
    }
  }

  private case class RequestInformation(filename: String, contentType: String, start: Long, total: Long)

  private object RequestInformation {
    def fromRequest(request: RequestHeader): Option[RequestInformation] = {
      val contentType = request.headers.get(CONTENT_TYPE).getOrElse("")
      val contentDisposition = request.headers.get(CONTENT_DISPOSITION)
      val filename: String = contentDisposition.flatMap(ContentDisposition(_).filename).getOrElse("")

      def one(start: Long, total: Long) = RequestInformation(filename, contentType, start, total)

      // A string matching "(\d{0,18})" cannot throw an exception when converted to Long.
      val rangeResults = request.headers.get(CONTENT_RANGE).flatMap { contentRanges =>
        """^bytes (\d{0,18})-\d+/(\d{0,18})$""".r.findFirstMatchIn(contentRanges).map { rangeMatch =>
          val List(start, total) = rangeMatch.subgroups.take(2)

          one(start.toLong, total.toLong)
        }
      }
      
      val lengthResults = request.headers.get(CONTENT_LENGTH).flatMap { contentLengths =>
        """^(\d{0,18})$""".r.findFirstMatchIn(contentLengths).map { lengthMatch =>
          val List(total) = lengthMatch.subgroups.take(1)
          one(0L, total.toLong)
        }
      }

      (rangeResults ++ lengthResults).headOption
    }
  }
}

object MassUploadFileIteratee extends MassUploadFileIteratee {
  import models.orm.stores.FileGroupStore
  import org.overviewproject.tree.orm.FileJobState.InProgress

  class DatabaseStorage extends Storage with PgConnection {

    override def createFileGroup(userEmail: String): FileGroup = OverviewDatabase.inTransaction {
      FileGroupStore.insertOrUpdate(FileGroup(userEmail, InProgress))
    }

    override def findCurrentFileGroup(userEmail: String): Option[FileGroup] = OverviewDatabase.inTransaction {
      FileGroupFinder.byUserAndState(userEmail, InProgress).headOption
    }

    override def createUpload(fileGroupId: Long, contentType: String, filename: String, guid: UUID, size: Long): GroupedFileUpload =
      withPgConnection { implicit c =>
        val upload = LO.withLargeObject { lo =>
          GroupedFileUpload(fileGroupId, guid, contentType, filename, size, 0, lo.oid)
        }

        OverviewDatabase.inTransaction {
          GroupedFileUploadStore.insertOrUpdate(upload)
        }
      }

    override def findUpload(fileGroupId: Long, guid: UUID): Option[GroupedFileUpload] = OverviewDatabase.inTransaction {
      GroupedFileUploadFinder.byFileGroupAndGuid(fileGroupId, guid).headOption
    }

    override def appendData(upload: GroupedFileUpload, data: Iterable[Byte]): GroupedFileUpload =
      withPgConnection { implicit c =>
        val uploadedSize = LO.withLargeObject(upload.contentsOid) { lo => lo.insert(data.toArray, upload.uploadedSize.toInt) }
        val updatedUpload = upload.copy(uploadedSize = uploadedSize)

        OverviewDatabase.inTransaction {
          GroupedFileUploadStore.insertOrUpdate(updatedUpload)
        }
      }

  }

  override val storage = new DatabaseStorage
}
