package controllers.util

import play.api.mvc._
import scala.concurrent.Future

import com.overviewdocs.database.DeprecatedDatabase

object TransactionAction extends ActionBuilder[Request] {
  override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) : Future[Result] = {
    DeprecatedDatabase.inTransaction(block(request))
  }
}
