// MassUploadController is factored into this file so that both client and API access route to same code
package controllers.util

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import java.util.UUID
import play.api.libs.json.{JsObject,Json}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction,RequestHeader,Result}
import scala.concurrent.Future
import scala.util.Try

import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import com.overviewdocs.models.{FileGroup,GroupedFileUpload}
import com.overviewdocs.util.ContentDisposition

private[controllers] object MassUploadControllerMethods extends controllers.Controller {
  case class Create(
    userEmail: String,
    apiToken: Option[String],
    guid: UUID,
    fileGroupBackend: FileGroupBackend, 
    groupedFileUploadBackend: GroupedFileUploadBackend,
    uploadSinkFactory: (GroupedFileUpload,Long) => Sink[ByteString, Future[Unit]],
    wantJsonResponse: Boolean
  ) extends EssentialAction {
    private case class RequestInfo(
      filename: String,
      contentType: String,
      metadataJson: Option[JsObject],
      start: Long,
      total: Long
    )
    
    private object RequestInfo {
      def fromRequest(request: RequestHeader): Either[String, RequestInfo] = {
        val contentType = request.headers.get(CONTENT_TYPE).getOrElse("")
        val contentDisposition = request.headers.get(CONTENT_DISPOSITION)
        val filename: String = contentDisposition.flatMap(ContentDisposition(_).filename).getOrElse("")

        if (filename == "") return Left("Invalid or missing Content-Disposition filename")

        val metadataJson: Option[JsObject] = request.headers.get("Overview-Document-Metadata-JSON") match {
          case None => None
          case Some(text) => {
            Try(Json.parse(text).as[JsObject]).toOption match {
              case None => return Left("Overview-Document-Metadata-JSON must be ASCII-encoded JSON Object")
              case Some(jsObject) => Some(jsObject)
            }
          }
        }

        def one(start: Long, total: Long) = RequestInfo(filename, contentType, metadataJson, start, total)

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

        (rangeResults ++ lengthResults).headOption.toRight("Request must have Content-Range or Content-Length header")
      }
    }

    private def badRequest(message: String): Accumulator[ByteString,Result] = {
      val result: Result = wantJsonResponse match {
        case true => BadRequest(jsonError("illegal-arguments", message))
        case false => BadRequest(message)
      }
      Accumulator(Sink.ignore).map(_ => result)
    }

    private def findOrCreateFileGroup: Future[FileGroup] = {
      val attributes = FileGroup.CreateAttributes(userEmail, apiToken)
      fileGroupBackend.findOrCreate(attributes)
    }

    private def findOrCreateGroupedFileUpload(fileGroup: FileGroup, info: RequestInfo): Future[GroupedFileUpload] = {
      val attributes = GroupedFileUpload.CreateAttributes(
        fileGroup.id,
        guid,
        info.contentType,
        info.filename,
        info.metadataJson,
        info.total
      )
      groupedFileUploadBackend.findOrCreate(attributes)
    }

    private def createAccumulator(upload: GroupedFileUpload, info: RequestInfo): Accumulator[ByteString,Result] = {
      if (info.start > upload.uploadedSize) {
        badRequest(s"Tried to resume past last uploaded byte. Resumed at byte ${info.start}, but only ${upload.uploadedSize} bytes have been uploaded.")
      } else {
        Accumulator(uploadSinkFactory(upload, info.start)).map(_ => Created)
      }
    }

    override def apply(request: RequestHeader): Accumulator[ByteString,Result] = {
      RequestInfo.fromRequest(request) match {
        case Left(message) => badRequest(message)
        case Right(info) => {
          val futureAccumulator: Future[Accumulator[ByteString,Result]] = 
            for {
              fileGroup <- findOrCreateFileGroup
              groupedFileUpload <- findOrCreateGroupedFileUpload(fileGroup, info)
            } yield createAccumulator(groupedFileUpload, info)

          Accumulator.flatten(futureAccumulator)(play.api.Play.current.materializer)
        }
      }
    }
  }
}
