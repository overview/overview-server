package controllers.filters

import javax.inject.Inject
import play.api.mvc.{EssentialAction,EssentialFilter,RequestHeader}
import scala.concurrent.ExecutionContext

/** Adds Strict-Transport-Security to all SSL responses.
  *
  * https://www.owasp.org/index.php/HTTP_Strict_Transport_Security_Cheat_Sheet
  */
class StrictTransportSecurityFilter @Inject() (implicit ec: ExecutionContext) extends EssentialFilter {
  private val Headers = Seq(
    "Strict-Transport-Security" -> "max-age=31536000"
    // We'd like to includeSubDomains, but for now we're using HTTP on jenkins-ci.overviewdocs.com
  )

  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      if (requestHeader.secure) {
        nextFilter(requestHeader).map(_.withHeaders(Headers: _*))
      } else {
        nextFilter(requestHeader)
      }
    }
  }
}
