package controllers.admin

import play.api.mvc.{Action,AnyContent,BodyParser,BodyParsers}

import models.orm.{User,UserRole}

trait AdminController extends controllers.BaseController {
  protected def adminAction[A](p: BodyParser[A])(f: User => ActionWithConnection[A]): Action[A] = {
    authorizedAction[A](p, adminUser)(f)
  }

  protected def adminAction(f: User => ActionWithConnection[AnyContent]): Action[AnyContent] = {
    adminAction(BodyParsers.parse.anyContent)(f)
  }

  protected def adminUser() : Authority = { user => user.role == UserRole.Administrator }
}
