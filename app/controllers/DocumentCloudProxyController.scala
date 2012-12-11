package controllers

import play.api.data.{Form,Forms}
import play.api.mvc.{Controller,ResponseHeader,SimpleResult}
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.WS

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import models.OverviewUser

/**
 * Pretends to be DocumentCloud.
 *
 * When the client POSTs to "projects.json", we call an authenticated GET to
 * DocumentCloud's "projects.json". (The client POSTs the email and password
 * for authentication.)
 *
 * Why does this exist at all? Because Internet Explorer can't send information
 * to a different domain than ours.
 */
trait DocumentCloudProxyController extends Controller {
  val DocumentCloudApiProjectsUrl = "https://www.documentcloud.org/api/projects.json"

  def projects() = AuthorizedAction(anyUser) { implicit request =>
    val form = Form(Forms.tuple(
      "email" -> Forms.text,
      "password" -> Forms.text
    ))
    form.bindFromRequest.fold(
      formWithErrors => BadRequest, // won't happen if the client is using our JS
      (tuple) => {
        val (email, password) = tuple // Perform NO validation--we want to be transaprent
        Async {
          WS.url(DocumentCloudApiProjectsUrl)
            .withAuth(email, password, com.ning.http.client.Realm.AuthScheme.BASIC)
            .get()
            .map({ wsResponse =>
              import collection.JavaConversions._
              val headerListsJava: java.util.Map[String,java.util.List[String]] = wsResponse.getAHCResponse.getHeaders
              val headerLists: Map[String,java.util.List[String]] = headerListsJava.iterator.toMap
              val headers: Map[String,String] = headerLists.mapValues(item => Option(item.get(0)).getOrElse(""))
              SimpleResult(ResponseHeader(wsResponse.status, headers), Enumerator(wsResponse.body))
            })
        }
      }
    )
  }
}

object DocumentCloudProxyController extends DocumentCloudProxyController
