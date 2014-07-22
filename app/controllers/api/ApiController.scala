package controllers.api

import play.api.libs.json.JsValue
import play.api.mvc.Controller

trait ApiController extends Controller {
  protected def jsonError(message: String) = views.json.api.error(message)
}
