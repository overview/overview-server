package controllers.util

import play.api.mvc._
import scala.concurrent.Future

import models.OverviewDatabase

object TransactionAction extends ActionBuilder[Request] {
  override protected def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[SimpleResult]) : Future[SimpleResult] = {
    OverviewDatabase.inTransaction(block(request))
  }
}
