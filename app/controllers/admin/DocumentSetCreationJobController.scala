package controllers.admin

import java.sql.Connection
import play.api.mvc.{AnyContent,Request}

import models.orm.DocumentSetCreationJob

object DocumentSetCreationJobController extends AdminController {
  def index() = adminAction((user: User) => authorizedIndex(user)(_: Request[AnyContent], _: Connection))

  def authorizedIndex(user: User)(implicit request: Request[AnyContent], connection: Connection) = {
    val jobs = DocumentSetCreationJob.all.toSeq
    Ok(views.html.admin.DocumentSetCreationJob.index(user, jobs))
  }
}
