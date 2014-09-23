package controllers.api

import play.api.libs.json.JsValue
import play.api.mvc.{Controller,RequestHeader}
import scala.util.control.Exception.catching

import models.pagination.PageRequest

trait ApiController extends Controller {
  protected def jsonError(message: String) = views.json.api.error(message)

  private def queryStringParamToUnsignedInt(request: RequestHeader, param: String): Option[Int] = {
    request.queryString
      .getOrElse(param, Seq())
      .headOption
      .flatMap((s) => catching(classOf[NumberFormatException]).opt(s.toInt))
      .filter((i) => i >= 0)
  }

  protected def pageRequest(request: RequestHeader, maxLimit: Int) = {
    val requestOffset = queryStringParamToUnsignedInt(request, "offset")
    val requestLimit = queryStringParamToUnsignedInt(request, "limit")

    val offset = requestOffset.getOrElse(0)
    val limit = math.max(1, math.min(maxLimit, requestLimit.getOrElse(maxLimit)))
    PageRequest(offset, limit)
  }
}
