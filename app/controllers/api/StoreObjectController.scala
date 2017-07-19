package controllers.api

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.{JsObject,JsPath,JsValue,Reads}
import scala.concurrent.Future
import scala.reflect.classTag

import controllers.backend.{StoreBackend,StoreObjectBackend}
import controllers.auth.Authorities.{anyUser,userOwningStoreObject}
import com.overviewdocs.models.StoreObject

class StoreObjectController @Inject() (
  storeBackend: StoreBackend,
  storeObjectBackend: StoreObjectBackend,
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController {

  def index = apiAuthorizedAction(anyUser).async { request =>
    for {
      store <- storeBackend.showOrCreate(request.apiToken.token)
      storeObjects <- storeObjectBackend.index(store.id)
    } yield Ok(views.json.api.StoreObject.index(storeObjects))
  }

  def create = apiAuthorizedAction(anyUser).async { request =>
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
          case None => Future.successful(BadRequest(jsonError("illegal-arguments",
            """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object). You may post an Array of such objects to create many objects with one request."""
          )))
        }
      }
    }
  }

  def show(id: Long) = apiAuthorizedAction(userOwningStoreObject(id)).async { request =>
    storeObjectBackend.show(id).map(_ match {
      case Some(storeObject) => Ok(views.json.api.StoreObject.show(storeObject))
      case None => NotFound
    })
  }

  def update(id: Long) = apiAuthorizedAction(userOwningStoreObject(id)).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(StoreObjectController.updateJsonReader) match {
      case Some(attributes) => {
        storeObjectBackend.update(id, attributes).map(_ match {
          case Some(storeObject) => Ok(views.json.api.StoreObject.show(storeObject))
          case None => NotFound(jsonError("not-found", "This StoreObject does not exist."))
        })
      }
      case None => Future(BadRequest(jsonError("illegal-arguments",
        """You must POST a JSON object with "indexedLong" (Number or null), "indexedString" (String or null) and "json" (possibly-empty Object)"""
      )))
    }
  }

  def destroy(id: Long) = apiAuthorizedAction(userOwningStoreObject(id)).async {
    storeObjectBackend.destroy(id).map(Unit => NoContent)
  }

  def destroyMany = apiAuthorizedAction(anyUser).async { request =>
    val body: JsValue = request.body.asJson.getOrElse(JsObject(Seq()))

    body.asOpt(StoreObjectController.deleteArrayJsonReader) match {
      case Some(ids) => {
        for {
          store <- storeBackend.showOrCreate(request.apiToken.token)
          unit <- storeObjectBackend.destroyMany(store.id, ids.toSeq)
        } yield NoContent
      }
      case None => Future.successful(BadRequest(jsonError("illegal-arguments", "You must POST a JSON Array of IDs.")))
    }
  }
}

object StoreObjectController {
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
