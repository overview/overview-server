package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsError,JsObject,JsPath,JsSuccess,JsValue,Reads}
import play.api.mvc.Result
import play.api.mvc.BodyParsers.parse
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.{userOwningDocumentSet,userOwningViz}
import controllers.backend.VizBackend
import org.overviewproject.models.Viz

trait VizController extends ApiController {
  protected val backend: VizBackend

  private val updateJsonReader: Reads[Viz.UpdateAttributes] = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json.Json

    (
      (JsPath \ "title").read[String](minLength[String](1)) and
      (JsPath \ "json").read[JsObject]
    )(Viz.UpdateAttributes.apply _)
  }

  def index(documentSetId: Long) = ApiAuthorizedAction(userOwningDocumentSet(documentSetId)).async { request =>
    backend.index(documentSetId).map { vizs =>
      Ok(views.json.api.Viz.index(vizs))
    }
  }

  def show(vizId: Long) = ApiAuthorizedAction(userOwningViz(vizId)).async { request =>
    backend.show(vizId).map { _ match {
      case None => NotFound
      case Some(viz) => Ok(views.json.api.Viz.show(viz))
    }}
  }

  def update(vizId: Long) = ApiAuthorizedAction(userOwningViz(vizId)).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    val result: Future[Either[Result,Viz]] = body.validate(updateJsonReader) match {
      case s: JsSuccess[Viz.UpdateAttributes] => {
        backend.update(vizId, s.get)
          .map(_.toRight(NotFound(jsonError(s"Could not see Viz ${vizId}; the database is unchanged"))))
      }
      case _: JsError => {
        Future(Left(BadRequest(jsonError(
          """You must POST a JSON object with "title" (non-empty String) and "json" (possibly-empty Object)"""
        ))))
      }
    }
    result.map(_ match {
      case Left(result) => result
      case Right(viz) => Ok(views.json.api.Viz.show(viz))
    })
  }
}

object VizController extends VizController {
  override val backend = VizBackend
}
