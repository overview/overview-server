import play.api.{GlobalSettings,Logger}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Iteratee
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

object CorsFilter extends EssentialFilter {
  private val CorsResponseHeaders = Seq(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Headers" -> "origin, authorization, content-type, accept",
    "Access-Control-Allow-Credentials" -> "true"
  )

  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      if (requestHeader.path.startsWith("/api/")) {
        nextFilter(requestHeader).map(_.withHeaders(CorsResponseHeaders: _*))
      } else {
        nextFilter(requestHeader)
      }
    }
  }
}

object Global extends WithFilters(LoggingFilter, CorsFilter, CSRFFilter()) with GlobalSettings {
  private val HttpOverrideKey = "X-HTTP-Method-Override"

  private val CorsOptionsHeaders = Seq(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "DELETE, PUT, POST, GET, OPTIONS", // laziness
    "Access-Control-Allow-Credentials" -> "true",
    "Access-Control-Allow-Headers" -> "origin, authorization",
    "Access-Control-Max-Age" -> "1728000"
  )

  private val optionsHandler = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      Iteratee.ignore[Array[Byte]].map(Unit => Results.NoContent.withHeaders(
        CorsOptionsHeaders: _*
      ))
    }
  }

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    // Handle OPTIONS requests.
    // This should be in Play. In the meantime, we just say every HTTP method
    // works on any /api/* URL.
    if (request.method == "OPTIONS" && request.path.startsWith("/api/")) {
      return Some(optionsHandler)
    }

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
