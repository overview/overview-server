package controllers.filters

import java.util.regex.Pattern
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction,EssentialFilter,RequestHeader,Results}
import scala.concurrent.Future

/** Forwards all requests to <tt>overview.canonical_url</tt>. */
class CanonicalHostFilter @Inject() (configuration: Configuration) extends EssentialFilter {
  private case class Config(canonicalUrl: String) {
    if (!canonicalUrl.matches("\\Ahttps?://[^/]+\\Z")) {
      throw new RuntimeException(s"The canonical URL you specified (OV_URL=${canonicalUrl}) is not valid. It must look like 'http://example.org'")
    }

    val wantSecure: Boolean = canonicalUrl.startsWith("https://")
    val wantHost: String = canonicalUrl.split("/")(2)
  }

  private val maybeUrl: Option[String] = configuration.get[Option[String]]("overview.canonical_url") match {
    case None => None
    case Some("") => None
    case Some(s) => Some(s)
  }
  private val maybeConfig: Option[Config] = maybeUrl.map(Config.apply _)

  def apply(next: EssentialAction) = EssentialAction { requestHeader =>
    maybeConfig match {
      case None => next(requestHeader)
      case Some(config) => {
        if (requestHeader.secure == config.wantSecure && requestHeader.host == config.wantHost) {
          next(requestHeader)
        } else {
          val newUri = config.canonicalUrl + requestHeader.uri
          Accumulator.done(Results.MovedPermanently(newUri))
        }
      }
    }
  }
}
