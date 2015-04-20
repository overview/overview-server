package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsObject,JsNumber}
import play.api.mvc.Result
import scala.concurrent.Future

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.{userViewingDocumentSet,userOwningTag}
import controllers.backend.TagDocumentBackend

trait TagDocumentController extends Controller with SelectionHelpers {
  protected val tagDocumentBackend: TagDocumentBackend

  def count(documentSetId: Long) = AuthorizedAction(userViewingDocumentSet(documentSetId)).async { request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => {
        for {
          ids <- selection.getAllDocumentIds
          counts <- tagDocumentBackend.count(documentSetId, ids)
        } yield {
          val fields: Seq[(String,JsNumber)] = counts.toSeq.map { (x: (Long,Int)) =>
            val key = x._1.toString
            val number = BigDecimal(x._2)
            key -> JsNumber(number)
          }
          Ok(JsObject(fields))
        }
      }
    })
  }

  def createMany(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningTag(documentSetId, tagId)).async { request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => for {
        documentIds <- selection.getAllDocumentIds
        _ <- tagDocumentBackend.createMany(tagId, documentIds)
      } yield Created
    })
  }

  def destroyMany(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningTag(documentSetId, tagId)).async { implicit request =>
    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => for {
        documentIds <- selection.getAllDocumentIds
        _ <- tagDocumentBackend.destroyMany(tagId, documentIds)
      } yield NoContent
    })
  }
}

object TagDocumentController extends TagDocumentController {
  override protected val tagDocumentBackend = TagDocumentBackend
}
