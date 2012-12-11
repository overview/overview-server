package controllers.admin

import java.sql.Connection
import play.api.mvc.{AnyContent,Request}

import models.orm.DocumentSetCreationJob
import models.OverviewUser

object DocumentSetCreationJobController extends AdminController {
  def index() = adminAction((user: OverviewUser) => authorizedIndex(user)(_: Request[AnyContent], _: Connection))

  def authorizedIndex(user: OverviewUser)(implicit request: Request[AnyContent], connection: Connection) = {
    val jobs = DocumentSetCreationJob.all.iterator.toSeq
    Ok(views.html.admin.DocumentSetCreationJob.index(user, jobs))
  }
}
