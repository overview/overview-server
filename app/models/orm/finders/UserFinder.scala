package models.orm.finders

import java.sql.Timestamp
import java.util.Date
import scala.language.postfixOps

import models.orm.Schema
import models.User
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.Ownership

object UserFinder extends Finder {
  /** @return All Users, sorted by email */
  def all : FinderResult[User] = {
    from(Schema.users)(u =>
      select(u)
      orderBy(u.lastActivityAt.isNull, u.lastActivityAt desc, u.email)
    )
  }

  /** @return All Users with the given email address.
    *
    * The result will have length 0 or 1.
    */
  def byEmail(email: String) : FinderResult[User] = {
    Schema.users.where((u) => lower(u.email) === lower(email))
  }

  /** @return All Users with the given ID.
    *
    * The result will have length 0 or 1.
    */
  def byId(id: Long) : FinderResult[User] = {
    Schema.users.where(_.id === id)
  }

  def byConfirmationToken(token: String) : FinderResult[User] = {
    Schema.users.where(u => u.confirmationToken === Some(token))
  }

  def byResetPasswordTokenAndMinDate(token: String, minDate: Date) = {
    Schema.users
      .where(u => u.resetPasswordToken === Some(token))
      .where(u => u.resetPasswordSentAt >= Some(new Timestamp(minDate.getTime)))
  }
}
