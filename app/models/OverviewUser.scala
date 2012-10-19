/*
 * OverviewUser.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package models

import java.util.Date
import java.sql.Timestamp
import models.orm.User
import org.joda.time.DateTime.now
import ua.t3hnar.bcrypt._

/**
 * A user that exists in the database
 */
trait OverviewUser {
  val id: Long
  val email: String

  val currentSignInAt: Option[Timestamp]
  val currentSignInIp: Option[String]
  val lastSignInAt: Option[Timestamp]
  val lastSignInIp: Option[String]

  def passwordMatches(password: String): Boolean

  /** @return None if the user has no open confirmation request */
  def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest]

  /** @return The same user, with a new reset-password token. Save to commit. */
  def withResetPasswordRequest: OverviewUser with ResetPasswordRequest

  def recordLogin(ip: String, date: Date): OverviewUser

  /** @return The same user, but saved in the database. (Users are immutable, conceptually.) */
  def save: OverviewUser
}

/**
 * A user that has an open confirmation request (confirmationToken exists)
 */
trait ConfirmationRequest {
  val confirmationToken: String
  val confirmationSentAt: Timestamp

  /**
   * After confirming, the values in ConfirmationRequest will still
   * be available, though the actual user will no longer have
   * a confirmationToken. It's better to work with the returned OverviewUser
   * to avoid any inconsistencies. The user must be saved before confirm
   * takes effect.
   */
  def confirm: OverviewUser
}

/**
 * A user that has an open password-reset token
 */
trait ResetPasswordRequest {
  val resetPasswordToken: String
  val resetPasswordSentAt: Timestamp

  /**
   * Converts this OverviewUser to one with the new password. Save the return
   * value to make the change permanent.
   */
  def withNewPassword(password: String): OverviewUser
}

/**
 * A user that may or may not exist yet, and in an unknown state.
 * Different methods attempt to convert the user into a known state,
 * returning None if conversion can't be completed.
 */
case class PotentialUser(val email: String, val password: String) {
  private val user = OverviewUser.findByEmail(email)

  /**
   * @return OverviewUser if exists, without checking password
   */
  def withRegisteredEmail: Option[OverviewUser] = {
    user
  }

  /**
   * @return OverviewUser if password is correct
   */
  def withValidCredentials: Option[OverviewUser] = {
    user.find(u => u.passwordMatches(password))
  }

  /**
   * @return OverviewUser with ConfirmationRequest if the user has an active confirmation request.
   */
  def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest] = {
    user match {
      case Some(u) => u.withConfirmationRequest
      case None => None
    }
  }

  /**
   * @return the OverviewUser with ConfirmationRequest. No matter what the state the user is in
   * a new confirmation request can always be generated. User must be saved before change takes
   * effect.
   */
  def requestConfirmation: OverviewUser with ConfirmationRequest =
    OverviewUser.prepareNewRegistration(email, password)

}

/**
 * Helpers to get new or existing OverviewUsers
 */
object OverviewUser {
  private val TokenLength = 26
  private val BcryptRounds = 7

  private def generateToken = scala.util.Random.alphanumeric.take(TokenLength).mkString
  private def generateTimestamp = new Timestamp(now().getMillis())

  def findById(id: Long): Option[OverviewUser] = create(User.findById(id))

  def findByEmail(email: String): Option[OverviewUser] = create(User.findByEmail(email))

  def findByResetPasswordTokenAndMinDate(token: String, minDate: Date): Option[OverviewUser with ResetPasswordRequest] = {
    val user = User.findByResetPasswordTokenAndMinDate(token, minDate)
    user.map(new UserWithResetPasswordRequest(_))
  }

  def findByConfirmationToken(token: String): Option[OverviewUser with ConfirmationRequest] = {
    val user = User.findByConfirmationToken(token)
    user.map(new UnconfirmedUser(_))
  }

  def prepareNewRegistration(email: String, password: String): OverviewUser with ConfirmationRequest = {
    val confirmationToken = generateToken
    val confirmationSentAt = generateTimestamp

    val user = User(email = email, passwordHash = password.bcrypt(BcryptRounds),
      confirmationToken = Some(confirmationToken),
      confirmationSentAt = Some(confirmationSentAt))
    new UnconfirmedUser(user)
  }

  private def create(userData: Option[User]): Option[OverviewUser] = {
    userData.map(new OverviewUserImpl(_))
  }

  def create(user: User): OverviewUser = new OverviewUserImpl(user)

  /**
   * Underlying implementation that manages the User object that is the conduit to the
   * database. As the user state is transformed, the underlying User is modified and
   * passed along
   */
  private case class OverviewUserImpl(user: User) extends OverviewUser {
    override val id = user.id
    override val email = user.email
    override val currentSignInAt = user.currentSignInAt
    override val currentSignInIp = user.currentSignInIp
    override val lastSignInAt = user.lastSignInAt
    override val lastSignInIp = user.lastSignInIp

    def passwordMatches(password: String): Boolean = {
      password.isBcrypted(user.passwordHash)
    }

    def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest] = {
      if (user.confirmationToken.isDefined) Some(new UnconfirmedUser(user))
      else None
    }

    def withResetPasswordRequest: OverviewUser with ResetPasswordRequest = {
      new UserWithResetPasswordRequest(user.copy(
        resetPasswordToken = Some(generateToken),
        resetPasswordSentAt = Some(generateTimestamp)
      ))
    }

    override def recordLogin(ip: String, date: java.util.Date) : OverviewUser = {
      new OverviewUserImpl(user.copy(
        lastSignInAt = user.currentSignInAt,
        lastSignInIp = user.currentSignInIp,
        currentSignInAt = Some(new java.sql.Timestamp(date.getTime())),
        currentSignInIp = Some(ip)
      ))
    }

    def save: OverviewUser = copy(user.save)
  }

  /**
   * A User with an active confirmation request
   */
  private class UnconfirmedUser(user: User) extends OverviewUserImpl(user) with ConfirmationRequest {
    override val confirmationToken = user.confirmationToken.get
    override val confirmationSentAt = user.confirmationSentAt.get

    override def confirm: OverviewUser = {
      user.confirmationToken = None
      user.confirmedAt = Some(new Timestamp(now().getMillis))

      this
    }
  }

  private class UserWithResetPasswordRequest(user: User) extends OverviewUserImpl(user) with ResetPasswordRequest {
    override val resetPasswordToken = user.resetPasswordToken.getOrElse(throw new Exception("logic"))
    override val resetPasswordSentAt = user.resetPasswordSentAt.getOrElse(throw new Exception("logic"))

    override def withNewPassword(password: String) : OverviewUser = {
      OverviewUserImpl(user.copy(
        resetPasswordToken=None,
        resetPasswordSentAt=None,
        passwordHash=password.bcrypt(BcryptRounds)
      ))
    }
  }
}
