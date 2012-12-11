package controllers

import java.sql.Connection
import play.api.data.{Form,Forms}
import play.api.mvc.{AnyContent,Request,ResponseHeader,SimpleResult}
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.WS

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
trait DocumentCloudProxyController extends BaseController {
  val DocumentCloudApiProjectsUrl = "https://www.documentcloud.org/api/projects.json"

  def projects() = authorizedAction(anyUser)(user => authorizedProjects(user)(_: Request[AnyContent], _: Connection))

  private[controllers] def authorizedProjects(user: OverviewUser)(implicit request: Request[AnyContent], connection: Connection) = {
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
