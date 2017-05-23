package controllers.api

import play.api.mvc.{Action,Results}

trait CorsOptionsController extends ApiController {
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

object CorsOptionsController extends CorsOptionsController
