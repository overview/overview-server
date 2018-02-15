package controllers.filters

import javax.inject.Inject
import play.api.mvc.{EssentialAction,EssentialFilter,RequestHeader}
import play.api.Logger
import scala.concurrent.ExecutionContext

/** Logs each request and how long it took.
  */
class LoggingFilter @Inject() (implicit ec: ExecutionContext) extends EssentialFilter {
  // Copy/paste of http://www.playframework.com/documentation/2.2.1/ScalaHttpFilters
  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      val startTime = System.currentTimeMillis
      nextFilter(requestHeader).map { result =>
        val endTime = System.currentTimeMillis
        val requestTime = endTime - startTime
        Logger.info(f"${requestTime}%3sms ${requestHeader.method}%s ${requestHeader.uri}%s -> ${result.header.status}%d")
        result.withHeaders("Request-Time" -> requestTime.toString)
      }
    }
  }
}
