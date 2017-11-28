package controllers.api

import play.api.libs.json.Json
import javax.inject.Inject

import controllers.auth.Authorities.anyUser
import views.json.api.ApiToken.{show=>render}

class ApiTokenController @Inject() (
  val controllerComponents: ApiControllerComponents
) extends ApiBaseController {
  def show = apiAuthorizedAction(anyUser) { request =>
    // This is so simple. No backend: we're done!
    Ok(render(request.apiToken))
  }
}
