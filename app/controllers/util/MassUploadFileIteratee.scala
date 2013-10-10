package controllers.util

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.http.HeaderNames._
import org.overviewproject.util.ContentDisposition
import play.api.mvc.Result
import java.util.UUID
import models.orm.finders.FileGroupFinder
import org.overviewproject.postgres.LO
import models.orm.stores.GroupedFileUploadStore
import models.OverviewDatabase

trait MassUploadFileIteratee {
  val DefaultBufferSize = 1024 * 1024

  val storage: Storage

  def apply(userEmail: String, request: RequestHeader, guid: UUID, bufferSize: Int = DefaultBufferSize): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] = {
    val fileGroup = storage.findCurrentFileGroup(userEmail)
      .getOrElse(storage.createFileGroup(userEmail))

    val info = RequestInformation(request)
    val initialUpload: Either[Result, GroupedFileUpload] =
      Right(storage.createUpload(fileGroup.id, info.contentType, info.filename, guid, info.total))

    var buffer = Array[Byte]()

    Iteratee.fold[Array[Byte], Either[Result, GroupedFileUpload]](initialUpload) { (upload, data) =>
      buffer ++= data
      upload.right.map { u =>
        if (buffer.size >= bufferSize) {
          val update = storage.appendData(u, buffer)
          buffer = Array[Byte]()
          update
        } else u
      }
    } mapDone { output =>
      if (buffer.size > 0) output.right.map(storage.appendData(_, buffer))
      else output
    }
  }

  trait Storage {
    def createFileGroup(userEmail: String): FileGroup
    def findCurrentFileGroup(userEmail: String): Option[FileGroup]
    def createUpload(fileGroupId: Long, contentType: String, filename: String, guid: UUID, size: Long): GroupedFileUpload
    def appendData(upload: GroupedFileUpload, data: Iterable[Byte]): GroupedFileUpload
  }

  private case class RequestInformation(filename: String, contentType: String,
      start: Long, end: Long, total: Long)
      
  private object RequestInformation {
    def apply(request: RequestHeader): RequestInformation = {
      val contentType = request.headers.get(CONTENT_TYPE).get
      val contentDisposition = request.headers.get(CONTENT_DISPOSITION).get
      val contentRange = request.headers.get(CONTENT_RANGE).get
      val range = """(\d+)-(\d+)/(\d+)""".r // start-end/length
      val rangeMatch = range.findFirstMatchIn(contentRange).get
      val List(start, end, length) = rangeMatch.subgroups.take(3)
      
      RequestInformation(ContentDisposition.filename(contentDisposition).get, contentType, 
          start.toLong, end.toLong, length.toLong)
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

    override def appendData(upload: GroupedFileUpload, data: Iterable[Byte]): GroupedFileUpload =
      withPgConnection { implicit c =>
        val uploadedSize = LO.withLargeObject(upload.contentsOid) { lo => lo.add(data.toArray) }
        val updatedUpload = upload.copy(uploadedSize = uploadedSize)

        OverviewDatabase.inTransaction {
          GroupedFileUploadStore.insertOrUpdate(updatedUpload)
        }
      }

  }

  override val storage = new DatabaseStorage
}
