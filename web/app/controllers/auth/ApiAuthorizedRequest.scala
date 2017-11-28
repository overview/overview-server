package controllers.auth

import play.api.mvc.{Request,WrappedRequest}

import com.overviewdocs.models.ApiToken

case class ApiAuthorizedRequest[+A](request: Request[A], val apiToken: ApiToken)
  extends WrappedRequest(request)
