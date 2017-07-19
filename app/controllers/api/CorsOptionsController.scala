package controllers.api

import javax.inject.Inject
import play.api.mvc.{Action,BaseController,ControllerComponents,Results}

class CorsOptionsController @Inject() (
  val controllerComponents: ControllerComponents
) extends BaseController {
  private val Headers = Seq(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "DELETE, PUT, POST, GET, OPTIONS", // laziness
    "Access-Control-Allow-Credentials" -> "true",
    "Access-Control-Allow-Headers" -> "origin, authorization",
    "Access-Control-Max-Age" -> "1728000"
  )

  def options(url: String) = Action { request =>
    Results.NoContent.withHeaders(Headers: _*)
  }
}
