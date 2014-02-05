package controllers

import play.api.Play
import play.api.data.{Form,Forms}
import play.api.mvc.{Controller,ResponseHeader,SimpleResult}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.iteratee.Enumerator
import play.api.libs.ws.WS
import scala.concurrent.Future

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
  val DocumentCloudApiProjectsPath = "api/projects.json"
  val DocumentCloudUrl = play.api.Play.maybeApplication.flatMap(_.configuration.getString("overview.documentcloud_url")).getOrElse("https://www.documentcloud.org/api/projects.json")
  val DocumentCloudApiProjectsUrl = s"$DocumentCloudUrl/$DocumentCloudApiProjectsPath"

  def projects() = AuthorizedAction(anyUser).async { implicit request =>
    val form = Form(Forms.tuple(
      "email" -> Forms.text,
      "password" -> Forms.text
    ))
    form.bindFromRequest.fold(
      formWithErrors => Future(BadRequest), // won't happen if the client is using our JS
      (tuple) => {
        val (email, password) = tuple // Perform NO validation--we want to be transaprent
        WS.url(DocumentCloudApiProjectsUrl)
          .withAuth(email, password, com.ning.http.client.Realm.AuthScheme.BASIC)
          .get() // Returns a Future
          .map({ wsResponse =>
            import collection.JavaConversions._
            val headerListsJava: java.util.Map[String,java.util.List[String]] = wsResponse.getAHCResponse.getHeaders
            val headerLists: Map[String,java.util.List[String]] = headerListsJava.iterator.toMap
            val headers: Map[String,String] = headerLists.mapValues(item => Option(item.get(0)).getOrElse(""))
            SimpleResult(ResponseHeader(wsResponse.status, headers), Enumerator(wsResponse.getAHCResponse.getResponseBodyAsBytes()))
          })
      }
    )
  }
}

object DocumentCloudProxyController extends DocumentCloudProxyController
