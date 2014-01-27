import play.api.{GlobalSettings,Logger}
import play.api.mvc._
import play.filters.csrf.CSRFFilter
import scala.concurrent.ExecutionContext.Implicits.global

object LoggingFilter extends Filter {
  /* Straight from http://www.playframework.com/documentation/2.1.1/ScalaHttpFilters */
  def apply(next: (RequestHeader) => Result)(rh: RequestHeader) = {
    val start = System.currentTimeMillis

    def logTime(result: PlainResult): Result = {
      val time = System.currentTimeMillis - start
      Logger.info(s"${rh.method} ${rh.uri} took ${time}ms and returned ${result.header.status}")
      result.withHeaders("Request-Time" -> time.toString)
    }

    Logger.info(s"${rh.method} ${rh.uri} ...")

    next(rh) match {
      case plain: PlainResult => logTime(plain)
      case async: AsyncResult => async.transform(logTime)
    }
  }
}

object Global extends WithFilters(LoggingFilter, CSRFFilter()) with GlobalSettings {
  private val HttpOverrideKey = "X-HTTP-Method-Override"

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    // Handle DELETE and PUT requests.
    // This should be in Play. See
    // http://play.lighthouseapp.com/projects/82401/tickets/761-work-with-x-http-method-override
    val requestWithMethod = request.queryString.get(HttpOverrideKey).flatMap(_.headOption).map({ overrideMethod =>
      request.copy(
        queryString = request.queryString - HttpOverrideKey,
        method = overrideMethod
      )
    }).getOrElse(request)

    super.onRouteRequest(requestWithMethod)
  }
}
