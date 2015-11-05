package models

import java.sql.Timestamp

import com.overviewdocs.models.UserRole

case class User(
  id: Long = 0L,
  email: String = "user@example.org",
  passwordHash: String = "",
  role: UserRole.Value = UserRole.NormalUser,
  confirmationToken: Option[String] = None,
  confirmationSentAt: Option[Timestamp] = None,
  confirmedAt: Option[Timestamp] = None,
  resetPasswordToken: Option[String] = None,
  resetPasswordSentAt: Option[Timestamp] = None,
  lastActivityAt: Option[Timestamp] = None,
  lastActivityIp: Option[String] = None,
  emailSubscriber: Boolean = false,
  treeTooltipsEnabled: Boolean = true
) {
  def isAdministrator = role == UserRole.Administrator
}

object User {
  private val TokenLength = 26
  private val BcryptRounds = 7

  def generateToken: String = scala.util.Random.alphanumeric.take(TokenLength).mkString

  def hashPassword(password: String): String = {
    import com.github.t3hnar.bcrypt._
    password.bcrypt(BcryptRounds)
  }

  def passwordMatchesHash(password: String, hash: String): Boolean = {
    import com.github.t3hnar.bcrypt._
    password.isBcrypted(hash)
  }

  case class CreateAttributes(
    email: String,
    passwordHash: String,
    role: UserRole.Value = UserRole.NormalUser,
    confirmationToken: Option[String] = None,
    confirmationSentAt: Option[Timestamp] = None,
    confirmedAt: Option[Timestamp] = None,
    resetPasswordToken: Option[String] = None,
    resetPasswordSentAt: Option[Timestamp] = None,
    lastActivityAt: Option[Timestamp] = None,
    lastActivityIp: Option[String] = None,
    emailSubscriber: Boolean = false,
    treeTooltipsEnabled: Boolean = true
  )
}
