package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsArray,JsNull,JsObject,JsPath,JsSuccess,JsValue,Json,Reads}
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.userOwningViz
import controllers.backend.DocumentVizObjectBackend
import org.overviewproject.models.DocumentVizObject

trait DocumentVizObjectController extends ApiController {
  val backend: DocumentVizObjectBackend

  def create(vizId: Long) = ApiAuthorizedAction(userOwningViz(vizId)).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsNull)

    body.asOpt(DocumentVizObjectController.createArgsReader) match {
      case None => Future.successful(BadRequest(jsonError(
        """You must POST a JSON Array of Array elements. Each element should look like [documentId,objectId] or [documentId,objectId,null...] or [documentId,objectId,{"arbitrary":"json object"}]."""
      )))
      case Some(args) => {
        backend.createMany(vizId, args)
          .map((results: Seq[DocumentVizObject]) => Created(DocumentVizObjectController.writeCreateResults(results)))
      }
    }
  }
}

object DocumentVizObjectController extends DocumentVizObjectController {
  override val backend = DocumentVizObjectBackend

  private val nullReads: Reads[Option[JsObject]] = new Reads[Option[JsObject]] {
    override def reads(json: JsValue) = JsSuccess(None)
  }

  /** Reads DocumentVizObjects from input.
    *
    * This parser is a bit lax: any non-JsObject will be transformed to None.
    */
  private lazy val createArgReader: Reads[DocumentVizObject] = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json.Json

    (
      JsPath(0).read[Long] and
      JsPath(1).read[Long] and
      (JsPath(2).readNullable[JsObject] orElse nullReads)
    )(DocumentVizObject.apply _)
  }

  private lazy val createArgsReader: Reads[Array[DocumentVizObject]] = {
    implicit val x = createArgReader
    Reads.ArrayReads[DocumentVizObject]
  }

  private def writeCreateResults(results: Seq[DocumentVizObject]) = {
    def one(dvo: DocumentVizObject) = dvo.json match {
      case Some(json) => Json.arr(dvo.documentId, dvo.vizObjectId, json)
      case None => Json.arr(dvo.documentId, dvo.vizObjectId)
    }
    val jsons = results.map(one(_))
    JsArray(jsons)
  }
}
