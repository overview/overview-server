import play.api.GlobalSettings

import play.api.mvc.{Handler,RequestHeader}

object Global extends GlobalSettings {
  private val HttpOverrideKey = "X-HTTP-Method-Override"

  override def onRouteRequest(request: RequestHeader): Option[Handler] = {
    // Handle DELETE and PUT requests.
    // This should be in Play. See
    // http://play.lighthouseapp.com/projects/82401/tickets/761-work-with-x-http-method-override
    val requestWithMethod = request.queryString.get(HttpOverrideKey).flatMap(_.headOption).map({ overrideMethod =>
      new RequestHeader() {
        override def headers = request.headers
        override def queryString = request.queryString - HttpOverrideKey
        override def path = request.path
        override def uri = request.uri
        override def method = overrideMethod
        override def remoteAddress = request.remoteAddress
      }
    }).getOrElse(request)

    super.onRouteRequest(requestWithMethod)
  }
}
