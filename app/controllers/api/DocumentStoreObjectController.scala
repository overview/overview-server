package controllers.api

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsArray,JsNull,JsObject,JsNumber,JsPath,JsSuccess,JsValue,Json,Reads}
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.backend.{StoreBackend,DocumentStoreObjectBackend,SelectionBackend}
import com.overviewdocs.models.DocumentStoreObject

class DocumentStoreObjectController @Inject() (
  val storeBackend: StoreBackend,
  val documentStoreObjectBackend: DocumentStoreObjectBackend,
  val selectionBackend: SelectionBackend,
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController with ApiSelectionHelpers {

  def createMany = apiAuthorizedAction(anyUser).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsNull)

    body.asOpt(DocumentStoreObjectController.createArgsReader) match {
      case None => Future.successful(BadRequest(jsonError("illegal-arguments",
        """You must POST a JSON Array of Array elements. Each element should look like [documentId,objectId] or [documentId,objectId,null...] or [documentId,objectId,{"arbitrary":"json object"}]."""
      )))
      case Some(args) => {
        for {
          store <- storeBackend.showOrCreate(request.apiToken.token)
          results <- documentStoreObjectBackend.createMany(store.id, args)
        } yield Created(DocumentStoreObjectController.writeCreateResults(results))
      }
    }
  }

  def destroyMany = apiAuthorizedAction(anyUser).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsNull)

    body.asOpt(DocumentStoreObjectController.destroyArgsReader) match {
      case None => Future.successful(BadRequest(jsonError("illegal-arguments",
        """You must POST a JSON Array of Array elements. Each element should look like [documentId,objectId]."""
      )))
      case Some(args) => {
        for {
          store <- storeBackend.showOrCreate(request.apiToken.token)
          unit <- documentStoreObjectBackend.destroyMany(store.id, args.toSeq)
        } yield NoContent
      }
    }
  }

  def countByObject = apiAuthorizedAction(anyUser).async { request =>

    def formatCounts(counts: Map[Long,Int]): JsValue = {
      def tupleToValue(tuple: Tuple2[Long,Int]): Tuple2[String,JsValue] = (tuple._1.toString -> JsNumber(tuple._2))
      val values: Seq[(String,JsValue)] = counts.toSeq.map(tupleToValue _)
      JsObject(values)
    }

    // FIXME untyped .get. Change the URL.
    val documentSetId: Long = request.apiToken.documentSetId.get

    requestToSelection(documentSetId, request).flatMap(_ match {
      case Left(result) => Future.successful(result)
      case Right(selection) => for {
        store <- storeBackend.showOrCreate(request.apiToken.token)
        counts <- documentStoreObjectBackend.countByObject(store.id, selection)
      } yield Ok(formatCounts(counts))
    })
  }
}

object DocumentStoreObjectController {
  private val nullReads: Reads[Option[JsObject]] = new Reads[Option[JsObject]] {
    override def reads(json: JsValue) = JsSuccess(None)
  }

  /** Reads DocumentStoreObjects from input.
    *
    * This parser is a bit lax: any non-JsObject will be transformed to None.
    */
  private lazy val createArgReader: Reads[DocumentStoreObject] = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json.Json

    (
      JsPath(0).read[Long] and
      JsPath(1).read[Long] and
      (JsPath(2).readNullable[JsObject] orElse nullReads)
    )(DocumentStoreObject.apply _)
  }

  private lazy val destroyArgReader: Reads[Tuple2[Long,Long]] = {
    import play.api.libs.functional.syntax._
    import play.api.libs.json.Reads._
    import play.api.libs.json.Json

    (JsPath(0).read[Long] and JsPath(1).read[Long]).tupled
  }

  private lazy val createArgsReader: Reads[Array[DocumentStoreObject]] = {
    implicit val x = createArgReader
    Reads.ArrayReads[DocumentStoreObject]
  }

  private lazy val destroyArgsReader: Reads[Array[Tuple2[Long,Long]]] = {
    implicit val x = destroyArgReader
    Reads.ArrayReads[Tuple2[Long,Long]]
  }

  private def writeCreateResults(results: Seq[DocumentStoreObject]) = {
    def one(dvo: DocumentStoreObject) = dvo.json match {
      case Some(json) => Json.arr(dvo.documentId, dvo.storeObjectId, json)
      case None => Json.arr(dvo.documentId, dvo.storeObjectId)
    }
    val jsons = results.map(one(_))
    JsArray(jsons)
  }
}
