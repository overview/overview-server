package controllers.admin

import play.api.mvc.{Action,AnyContent,BodyParser,BodyParsers}

import controllers.auth.Authority
import models.OverviewUser

trait AdminController extends controllers.BaseController {
  protected def adminAction[A](p: BodyParser[A])(f: OverviewUser => ActionWithConnection[A]): Action[A] = {
    authorizedAction[A](p, adminUser)(f)
  }

  protected def adminAction(f: OverviewUser => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    adminAction(BodyParsers.parse.anyContent)(f)
  }

  protected def adminUser = new Authority {
    def apply(user: OverviewUser) = user.isAdministrator
  }
}
