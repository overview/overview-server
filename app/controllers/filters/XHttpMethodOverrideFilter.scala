package controllers.filters

import akka.stream.Materializer
import javax.inject.Inject
import play.api.mvc.{EssentialAction,EssentialFilter,RequestHeader}
import scala.concurrent.ExecutionContext

/** Changes the incoming Request's method to DELETE or PUT, depending on the
  * value of the X-HTTP-Method-Override HTTP header.
  *
  * This is stupid, and we should remove it. It's a relic of a time when Play
  * didn't support PUT and DELETE ... or at least, documented this alternative.
  * This may allow attackers to circumvent CSRF.
  *
  * XXX SECURITY TODO nix this throughout!
  */
class XHttpMethodOverrideFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends EssentialFilter {
  private val HeaderName = "X-HTTP-Method-Override"

  def apply(nextFilter: EssentialAction) = new EssentialAction {
    def apply(requestHeader: RequestHeader) = {
      requestHeader.queryString.get(HeaderName).flatMap(_.headOption) match {
        case Some("DELETE") => nextFilter(requestHeader.copy(method="DELETE"))
        case _ => nextFilter(requestHeader)
      }
    }
  }
}
