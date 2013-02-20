import play.api.GlobalSettings

import play.api.mvc.{Handler,RequestHeader}

object Global extends GlobalSettings {
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
