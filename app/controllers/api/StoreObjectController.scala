package controllers.api

import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{JsObject,JsPath,JsValue,Reads}
import scala.concurrent.Future
import scala.reflect.classTag

import controllers.backend.{StoreBackend,StoreObjectBackend}
import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.{anyUser,userOwningStoreObject}
import org.overviewproject.models.StoreObject

trait StoreObjectController extends ApiController {
  protected val storeBackend: StoreBackend
  protected val storeObjectBackend: StoreObjectBackend

  def index = ApiAuthorizedAction(anyUser).async { request =>
    for {
      store <- storeBackend.showOrCreate(request.apiToken.token)
      storeObjects <- storeObjectBackend.index(store.id)
    } yield Ok(views.json.api.StoreObject.index(storeObjects))
  }

  def create = ApiAuthorizedAction(anyUser).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(StoreObjectController.createJsonReader) match {
      case Some(attributes) => {
        for {
          store <- storeBackend.showOrCreate(request.apiToken.token)
          storeObject <- storeObjectBackend.create(store.id, attributes)
        } yield Ok(views.json.api.StoreObject.show(storeObject))
      }
      case None => {
        body.asOpt(StoreObjectController.createArrayJsonReader) match {
          case Some(attributesArray) => {
            for {
              store <- storeBackend.showOrCreate(request.apiToken.token)
              storeObjects <- storeObjectBackend.createMany(store.id, attributesArray.toSeq)
            } yield Ok(views.json.api.StoreObject.index(storeObjects))
          }
          case None => Future.successful(BadRequest(jsonError(
            """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object). You may post an Array of such objects to create many objects with one request."""
          )))
        }
      }
    }
  }

  def show(id: Long) = ApiAuthorizedAction(userOwningStoreObject(id)).async { request =>
    storeObjectBackend.show(id).map(_ match {
      case Some(storeObject) => Ok(views.json.api.StoreObject.show(storeObject))
      case None => NotFound
    })
  }

  def update(id: Long) = ApiAuthorizedAction(userOwningStoreObject(id)).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(StoreObjectController.updateJsonReader) match {
      case Some(attributes) => {
        storeObjectBackend.update(id, attributes).map(_ match {
          case Some(storeObject) => Ok(views.json.api.StoreObject.show(storeObject))
          case None => NotFound(jsonError("This StoreObject does not exist."))
        })
      }
      case None => Future(BadRequest(jsonError(
        """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object)"""
      )))
    }
  }

  def destroy(id: Long) = ApiAuthorizedAction(userOwningStoreObject(id)).async {
    storeObjectBackend.destroy(id).map(Unit => NoContent)
  }

  def destroyMany = ApiAuthorizedAction(anyUser).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(StoreObjectController.deleteArrayJsonReader) match {
      case Some(ids) => {
        for {
          store <- storeBackend.showOrCreate(request.apiToken.token)
          unit <- storeObjectBackend.destroyMany(store.id, ids.toSeq)
        } yield NoContent
      }
      case None => Future.successful(BadRequest(jsonError("You must POST a JSON Array of IDs.")))
    }
  }
}

object StoreObjectController extends StoreObjectController {
  override protected val storeBackend = StoreBackend
  override protected val storeObjectBackend = StoreObjectBackend

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

  private val createJsonReader = createReader(StoreObject.CreateAttributes.apply _)
  private val updateJsonReader = createReader(StoreObject.UpdateAttributes.apply _)
  private val deleteArrayJsonReader: Reads[Array[Long]] = {
    Reads.ArrayReads[Long]
  }

  private val createArrayJsonReader: Reads[Array[StoreObject.CreateAttributes]] = {
    implicit val x = createJsonReader
    Reads.ArrayReads[StoreObject.CreateAttributes]
  }
}
