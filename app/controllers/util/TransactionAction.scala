package controllers.util

import play.api.mvc.{Action, BodyParser, BodyParsers, AnyContent, Request, Result}

import models.OverviewDatabase

trait TransactionAction[A] extends Action[A] {
  val block: (Request[A] => Result)

  def apply(request: Request[A]): Result = OverviewDatabase.inTransaction {
    block(request)
  }
}

object TransactionAction {
  /** Creates a new TransactionAction.
    *
    * aBlock is guaranteed to run within a transaction. If it does not throw
    * an exception, the transaction will subsequently be committed.
    *
    * The BodyParser will be wrapped by HttpsEnforcer.
    */
  def apply[A](bodyParser: BodyParser[A])(aBlock: Request[A] => Result): TransactionAction[A] = new TransactionAction[A] {
    override def parser = HttpsEnforcer.HttpsBodyParser(bodyParser)
    override val block = aBlock
  }

  def apply(block: Request[AnyContent] => Result): TransactionAction[AnyContent] = {
    apply(HttpsEnforcer.HttpsBodyParser(BodyParsers.parse.anyContent))(block)
  }
}
