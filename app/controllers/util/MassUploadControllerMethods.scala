package controllers.util

import java.util.UUID
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{EssentialAction,RequestHeader,Result}
import scala.concurrent.Future

import controllers.auth.{ApiAuthorizedAction,ApiTokenFactory}
import controllers.auth.Authorities.anyUser
import controllers.backend.{ FileGroupBackend, GroupedFileUploadBackend }
import controllers.forms.MassUploadControllerForm
import controllers.iteratees.GroupedFileUploadIteratee
import models.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import models.OverviewDatabase
import org.overviewproject.models.{ApiToken,FileGroup,GroupedFileUpload}
import org.overviewproject.jobs.models.ClusterFileGroup
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob,DocumentSetUser}
import org.overviewproject.tree.Ownership
import org.overviewproject.util.ContentDisposition

private[controllers] object MassUploadControllerMethods extends controllers.Controller {
  case class Create(
    guid: UUID,
    fileGroupBackend: FileGroupBackend, 
    groupedFileUploadBackend: GroupedFileUploadBackend,
    apiTokenFactory: ApiTokenFactory,
    uploadIterateeFactory: (GroupedFileUpload,Long) => Iteratee[Array[Byte],Unit],
    wantJsonResponse: Boolean
  ) extends EssentialAction {
    private case class RequestInfo(filename: String, contentType: String, start: Long, total: Long)
    
    private object RequestInfo {
      def fromRequest(request: RequestHeader): Option[RequestInfo] = {
        val contentType = request.headers.get(CONTENT_TYPE).getOrElse("")
        val contentDisposition = request.headers.get(CONTENT_DISPOSITION)
        val filename: String = contentDisposition.flatMap(ContentDisposition(_).filename).getOrElse("")

        def one(start: Long, total: Long) = RequestInfo(filename, contentType, start, total)

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

    private def badRequest(message: String): Iteratee[Array[Byte],Result] = {
      val result: Result = wantJsonResponse match {
        case true => BadRequest(jsonError(message))
        case false => BadRequest(message)
      }
      Iteratee.ignore.map(_ => result)
    }

    private def findOrCreateFileGroup(apiToken: ApiToken): Future[FileGroup] = {
      val attributes = FileGroup.CreateAttributes(apiToken.createdBy, Some(apiToken.token))
      fileGroupBackend.findOrCreate(attributes)
    }

    private def findOrCreateGroupedFileUpload(fileGroup: FileGroup, info: RequestInfo): Future[GroupedFileUpload] = {
      val attributes = GroupedFileUpload.CreateAttributes(
        fileGroup.id,
        guid,
        info.contentType,
        info.filename,
        info.total
      )
      groupedFileUploadBackend.findOrCreate(attributes)
    }

    private def createIteratee(upload: GroupedFileUpload, info: RequestInfo): Iteratee[Array[Byte],Result] = {
      if (info.start > upload.uploadedSize) {
        badRequest(s"Tried to resume past last uploaded byte. Resumed at byte ${info.start}, but only ${upload.uploadedSize} bytes have been uploaded.")
      } else {
        uploadIterateeFactory(upload, info.start).map(_ => Created)
      }
    }

    override def apply(request: RequestHeader): Iteratee[Array[Byte],Result] = {
      val futureApiToken: Future[Either[Result,ApiToken]] = apiTokenFactory.loadAuthorizedApiToken(request, anyUser)
      val futureIteratee: Future[Iteratee[Array[Byte],Result]] = futureApiToken.flatMap(_ match {
        case Left(result) => Future.successful(Iteratee.ignore.map(_ => result))
        case Right(apiToken) => {
          RequestInfo.fromRequest(request) match {
            case Some(info) => {
              for {
                fileGroup <- findOrCreateFileGroup(apiToken)
                groupedFileUpload <- findOrCreateGroupedFileUpload(fileGroup, info)
              } yield createIteratee(groupedFileUpload, info)
            }
            case None => Future.successful(badRequest(("Request must have Content-Range or Content-Length header")))
          }
        }
      })

      Iteratee.flatten(futureIteratee)
    }
  }
}
