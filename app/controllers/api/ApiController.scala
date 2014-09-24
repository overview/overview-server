package controllers.api

import controllers.Controller

trait ApiController extends Controller {
  protected def jsonError(message: String) = views.json.api.error(message)
}
