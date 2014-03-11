import play.api.{GlobalSettings,Logger}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.mvc._
import play.filters.csrf.CSRFFilter
import scala.concurrent.Future

object LoggingFilter extends EssentialFilter {
  // Copy/paste of http://www.playframework.com/documentation/2.2.1/ScalaHttpFilters
  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      val shouldLog = !requestHeader.path.startsWith("/assets/")

      if (shouldLog) {
        val startTime = System.currentTimeMillis
        nextFilter(requestHeader).map { result =>
          val endTime = System.currentTimeMillis
          val requestTime = endTime - startTime
          Logger.info(f"${requestTime}%3sms ${requestHeader.method}%s ${requestHeader.uri}%s -> ${result.header.status}%d")
          result.withHeaders("Request-Time" -> requestTime.toString)
        }
      } else {
        nextFilter(requestHeader)
      }
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
