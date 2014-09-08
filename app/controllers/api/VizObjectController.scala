package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsError,JsObject,JsPath,JsSuccess,JsValue,Reads}
import scala.concurrent.Future
import scala.reflect.classTag

import controllers.backend.VizObjectBackend
import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.{userOwningViz,userOwningVizObject}
import org.overviewproject.models.VizObject

trait VizObjectController extends ApiController {
  protected val backend: VizObjectBackend

  def index(vizId: Long) = ApiAuthorizedAction(userOwningViz(vizId)).async { request =>
    backend.index(vizId).map { vizObjects =>
      Ok(views.json.api.VizObject.index(vizObjects))
    }
  }

  def create(vizId: Long) = ApiAuthorizedAction(userOwningViz(vizId)).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(VizObjectController.createJsonReader) match {
      case Some(attributes) => {
        backend.create(vizId, attributes)
          .map((vizObject: VizObject) => Ok(views.json.api.VizObject.show(vizObject)))
      }
      case None => {
        body.asOpt(VizObjectController.createArrayJsonReader) match {
          case Some(attributesArray) => {
            backend.createMany(vizId, attributesArray.toSeq)
              .map((vizObjects: Seq[VizObject]) => Ok(views.json.api.VizObject.index(vizObjects)))
          }
          case None => Future(BadRequest(jsonError(
            """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object). You may post an Array of such objects to create many objects with one request."""
          )))
        }
      }
    }
  }

  def show(vizId: Long, id: Long) = ApiAuthorizedAction(userOwningVizObject(vizId, id)).async { request =>
    backend.show(id).map(_ match {
      case Some(vizObject) => Ok(views.json.api.VizObject.show(vizObject))
      case None => NotFound
    })
  }

  def update(vizId: Long, id: Long) = ApiAuthorizedAction(userOwningVizObject(vizId, id)).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(VizObjectController.updateJsonReader) match {
      case Some(attributes) => {
        backend.update(id, attributes).map(_ match {
          case Some(vizObject) => Ok(views.json.api.VizObject.show(vizObject))
          case None => NotFound(jsonError("your VizObject disappeared in an icky race we figured would never happen"))
        })
      }
      case None => Future(BadRequest(jsonError(
        """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object)"""
      )))
    }
  }

  def destroy(vizId: Long, id: Long) = ApiAuthorizedAction(userOwningVizObject(vizId, id)).async {
    backend.destroy(id).map(Unit => NoContent)
  }
}

object VizObjectController extends VizObjectController {
  override val backend = VizObjectBackend

  private def createReader[T](ctor: (Option[Long], Option[String], JsObject) => T): Reads[T] = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json.Json

    (
      (JsPath \ "indexedLong").readNullable[Long] and
      (JsPath \ "indexedString").readNullable[String] and
      (JsPath \ "json").read[JsObject]
    )(ctor)
  }

  private val createJsonReader = createReader(VizObject.CreateAttributes.apply _)
  private val updateJsonReader = createReader(VizObject.UpdateAttributes.apply _)

  private val createArrayJsonReader: Reads[Array[VizObject.CreateAttributes]] = {
    implicit val x = createJsonReader
    Reads.ArrayReads[VizObject.CreateAttributes]
  }
}
