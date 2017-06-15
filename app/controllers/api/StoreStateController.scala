package controllers.api

import javax.inject.Inject
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.JsObject
import scala.concurrent.Future

import controllers.auth.ApiAuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.backend.StoreBackend

class StoreStateController @Inject() (
  backend: StoreBackend
) extends ApiController {

  def show = ApiAuthorizedAction(anyUser).async { request =>
    for {
      store <- backend.showOrCreate(request.apiToken.token)
    } yield Ok(store.json)
  }

  def update = ApiAuthorizedAction(anyUser).async { request =>
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
