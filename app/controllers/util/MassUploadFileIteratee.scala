package controllers.util

import java.util.UUID
import play.api.http.HeaderNames._
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.{Enumeratee,Iteratee,Traversable}
import play.api.Logger
import play.api.mvc.RequestHeader
import scala.concurrent.Future

import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import org.overviewproject.postgres.LO
import org.overviewproject.models.{FileGroup,GroupedFileUpload}
import org.overviewproject.util.ContentDisposition

trait MassUploadFileIteratee {
  val DefaultBufferSize = 1024 * 1024

  protected val fileGroupBackend: FileGroupBackend
  protected val groupedFileUploadBackend: GroupedFileUploadBackend

  sealed trait Result
  case class BadRequest(message: String) extends Result
  case object Ok extends Result

  private def badRequest(message: String): Iteratee[Array[Byte], Result] = {
    Iteratee.ignore.map(_ => BadRequest(message))
  }

  private def bufferedUploadIteratee(upload: GroupedFileUpload, position: Long, bufferSize: Int): Iteratee[Array[Byte], Result] = {
    if (position > upload.uploadedSize) {
      badRequest(s"Tried to resume past last uploaded byte. Resumed at byte ${position}, but only ${upload.uploadedSize} bytes have been uploaded.")
    } else {
      val it: Iteratee[Array[Byte], Result] = uploadIteratee(upload.id, position)
      val consumeOneChunk = Traversable.takeUpTo[Array[Byte]](bufferSize).transform(Iteratee.consume())
      val consumeChunks: Enumeratee[Array[Byte], Array[Byte]] = Enumeratee.grouped(consumeOneChunk)
      consumeChunks.transform(it)
    }
  }

  private def uploadIteratee(id: Long, initialPosition: Long): Iteratee[Array[Byte], Result] = {
    Iteratee.foldM(initialPosition) { (position: Long, bytes: Array[Byte]) =>
      bytes.length match {
        case 0 => Future.successful(position)
        case _ => {
          for {
            _ <- groupedFileUploadBackend.writeBytes(id, position, bytes)
          } yield position + bytes.length
        }
      }
    }
      .map { _ => Ok }
  }

  def apply(userEmail: String, request: RequestHeader, guid: UUID, bufferSize: Int = DefaultBufferSize): Iteratee[Array[Byte], Result] = {
    RequestInformation.fromRequest(request) match {
      case None => badRequest("Request did not specify Content-Range or Content-Length")
      case Some(requestInformation) => {
        val futureIteratee = for { 
          fileGroup <- fileGroupBackend.findOrCreate(FileGroup.CreateAttributes(userEmail, None))
          upload <- groupedFileUploadBackend.findOrCreate(GroupedFileUpload.CreateAttributes(
            fileGroup.id,
            guid,
            requestInformation.contentType,
            requestInformation.filename,
            requestInformation.total
          ))
        } yield bufferedUploadIteratee(upload, requestInformation.start, bufferSize)
        Iteratee.flatten(futureIteratee)
      }
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
  override protected val fileGroupBackend = FileGroupBackend
  override protected val groupedFileUploadBackend = GroupedFileUploadBackend
}
