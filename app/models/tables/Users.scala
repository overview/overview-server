package models.tables

import java.sql.Timestamp

import models.User
import org.overviewproject.models.UserRole
import org.overviewproject.database.Slick.api._

class UsersImpl(tag: Tag) extends Table[User](tag, "user") {
  def id = column[Long]("id", O.PrimaryKey)
  def email = column[String]("email")
  def passwordHash = column[String]("password_hash")
  def role = column[UserRole.Value]("role")(userRoleColumnType)
  def confirmationToken = column[Option[String]]("confirmation_token")
  def confirmationSentAt = column[Option[Timestamp]]("confirmation_sent_at")
  def confirmedAt = column[Option[Timestamp]]("confirmed_at")
  def resetPasswordToken = column[Option[String]]("reset_password_token")
  def resetPasswordSentAt = column[Option[Timestamp]]("reset_password_sent_at")
  def lastActivityAt = column[Option[Timestamp]]("last_activity_at")
  def lastActivityIp = column[Option[String]]("last_activity_ip")
  def emailSubscriber = column[Boolean]("email_subscriber")
  def treeTooltipsEnabled = column[Boolean]("tree_tooltips_enabled")

  def * = (
    id,
    email,
    passwordHash,
    role,
    confirmationToken,
    confirmationSentAt,
    confirmedAt,
    resetPasswordToken,
    resetPasswordSentAt,
    lastActivityAt,
    lastActivityIp,
    emailSubscriber,
    treeTooltipsEnabled
  ) <> ((User.apply _).tupled, User.unapply)

  def createAttributes = (
    email,
    passwordHash,
    role,
    confirmationToken,
    confirmationSentAt,
    confirmedAt,
    resetPasswordToken,
    resetPasswordSentAt,
    lastActivityAt,
    lastActivityIp,
    emailSubscriber,
    treeTooltipsEnabled
  ) <> (User.CreateAttributes.tupled, User.CreateAttributes.unapply)
}

object Users extends TableQuery(new UsersImpl(_))
