package controllers.api

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsObject
import scala.concurrent.Future

import controllers.auth.Authorities.anyUser
import controllers.backend.StoreBackend

class StoreStateController @Inject() (
  backend: StoreBackend,
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController {

  def show = apiAuthorizedAction(anyUser).async { request =>
    for {
      store <- backend.showOrCreate(request.apiToken.token)
    } yield Ok(store.json)
  }

  def update = apiAuthorizedAction(anyUser).async { request =>
    val body: Option[JsObject] = request.body.asJson.flatMap(_.asOpt[JsObject])

    body match {
      case Some(json) => {
        for {
          store <- backend.upsert(request.apiToken.token, json)
        } yield Ok(store.json)
      }
      case None => Future.successful(BadRequest(jsonError("illegal-arguments", "You must POST a JSON Object.")))
    }
  }
}
