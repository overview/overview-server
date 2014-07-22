package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import org.overviewproject.models.ApiToken

case class ApiAuthorizedRequest[A](request: Request[A], val apiToken: ApiToken)
  extends WrappedRequest(request)
