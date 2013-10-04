package controllers.util

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.GroupedFileUpload
import play.api.libs.iteratee.Iteratee
import play.api.mvc.RequestHeader
import play.api.http.HeaderNames._
import org.overviewproject.util.ContentDisposition
import play.api.mvc.Result

trait MassUploadFileIteratee {
  val DefaultBufferSize = 1024 * 1024

  val storage: Storage

  def apply(request: RequestHeader, bufferSize: Int = DefaultBufferSize): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] = {
    val fileGroup = storage.findCurrentFileGroup.get
    val info = RequestInformation(request)
    val initialUpload: Either[Result, GroupedFileUpload] =
      Right(storage.createUpload(fileGroup.id, info.contentType, info.filename, info.total))

    var buffer = Array[Byte]()
    
    Iteratee.fold[Array[Byte], Either[Result, GroupedFileUpload]](initialUpload) { (upload, data) =>
      upload.right.map { u =>
        if (buffer.size + data.size >= bufferSize) {
          val bufferedData = buffer ++ data
          buffer = Array[Byte]()
          storage.appendData(u, bufferedData)
        }
        else {
          buffer = buffer ++ data
          u
        }
      }
    } mapDone { output =>
      if (buffer.size > 0) output.right.map (storage.appendData(_, buffer))
      else output
    }
  }

  trait Storage {
    def findCurrentFileGroup: Option[FileGroup]
    def createUpload(fileGroupId: Long, contentType: String, filename: String, size: Long): GroupedFileUpload
    def appendData(upload: GroupedFileUpload, data: Array[Byte]): GroupedFileUpload
  }

  private case class RequestInformation(filename: String, contentType: String, start: Long, end: Long, total: Long)
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
