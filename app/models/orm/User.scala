package models.orm

import java.sql.Timestamp
import org.squeryl.dsl.ManyToMany
import org.squeryl.{KeyedEntity,Query}
import scala.annotation.target.field

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.Ownership
import models.orm.stores.DocumentSetUserStore

case class User(
  val id: Long = 0L,
  val email: String = "user@example.org",
  var passwordHash: String = "",
  var role: UserRole.UserRole = UserRole.NormalUser,
  var confirmationToken: Option[String] = None,
  var confirmationSentAt: Option[Timestamp] = None,
  var confirmedAt: Option[Timestamp] = None,
  var resetPasswordToken: Option[String] = None,
  var resetPasswordSentAt: Option[Timestamp] = None,
  var currentSignInAt: Option[Timestamp] = None,
  var currentSignInIp: Option[String] = None,
  var lastSignInAt: Option[Timestamp] = None,
  var lastSignInIp: Option[String] = None,
  var lastActivityAt: Option[Timestamp] = None,
  var lastActivityIp: Option[String] = None,
  val emailSubscriber: Boolean = false,
  val treeTooltipsEnabled: Boolean = true
  ) extends KeyedEntity[Long] {

  def this() = this(role = UserRole.NormalUser)

  // https://www.assembla.com/spaces/squeryl/tickets/68-add-support-for-full-updates-on-immutable-case-classes#/followers/ticket:68
  override def isPersisted(): Boolean = (id > 0)

  def save: User = Schema.users.insertOrUpdate(this)

  def delete = Schema.users.delete(id)
}

object User {
  private val TokenLength = 26
  private val BcryptRounds = 7

  //def all() = from(Schema.users)(u => select(u).orderBy(u.email.asc))
  def all() = from(Schema.users)(u => select(u).orderBy(u.lastActivityAt.isNull, u.lastActivityAt.desc, u.confirmedAt.desc))

  def findById(id: Long) = Schema.users.lookup(id)

  def findByEmail(email: String): Option[User] = {
    Schema.users.where(u => lower(u.email) === lower(email)).headOption
  }

  def findByConfirmationToken(token: String): Option[User] = {
    Schema.users.where(u => u.confirmationToken === Some(token)).headOption
  }

  def findByResetPasswordTokenAndMinDate(token: String, minDate: java.util.Date): Option[User] = {
    Schema.users
      .where(u => u.resetPasswordToken === Some(token))
      .where(u => u.resetPasswordSentAt >= Some(new Timestamp(minDate.getTime)))
      .headOption
  }
}
