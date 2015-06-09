/*
 * OverviewUser.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package models

import com.github.t3hnar.bcrypt._
import java.sql.Timestamp
import java.util.Date

import models.tables.Users
import org.overviewproject.database.BlockingDatabaseProvider
import org.overviewproject.models.UserRole

/**
 * A user that exists in the database
 */
trait OverviewUser {
  val id: Long
  val email: String
  val requestedEmailSubscription: Boolean

  val treeTooltipsEnabled: Boolean

  def passwordMatches(password: String): Boolean

  /** @return None if the user has no open confirmation request */
  def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest]

  /** @return None if the user has not confirmed */
  def asConfirmed: Option[OverviewUser with Confirmation]

  /** @return The same user, with a new reset-password token. Save to commit. */
  def withResetPasswordRequest: OverviewUser with ResetPasswordRequest

  /** @return The same user, with a different email */
  def withEmail(email: String): OverviewUser
  
  /** @return True if the user has permission to administer the website */
  def isAdministrator: Boolean

  /** Returns a User for storage in the database. */
  def toUser: User
}

/**
 * A user that has an open confirmation request (confirmationToken exists)
 */
trait ConfirmationRequest {
  val confirmationToken: String
  val confirmationSentAt: Date

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
  val resetPasswordSentAt: Date

  /**
   * Converts this OverviewUser to one with the new password. Save the return
   * value to make the change permanent.
   */
  def withNewPassword(password: String): OverviewUser
}

/**
 * A confirmed user (who has logged in at least once)
 */
trait Confirmation {
  val confirmedAt: Date
}

/**
 * A user that may or may not exist yet, and in an unknown state.
 * Different methods attempt to convert the user into a known state,
 * returning None if conversion can't be completed.
 */
case class PotentialUser(val email: String, val password: String, private val user: Option[OverviewUser]) {

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

}


object PotentialUser {
  def apply(email: String, password: String): PotentialUser = {
    PotentialUser(email, password, OverviewUser.findByEmail(email))
  }
}

/**
 * Potential new users have the option to subscribe to email announcements.
 */
class PotentialNewUser(email: String, password: String, val emailSubscriber: Boolean, user: Option[OverviewUser]) extends PotentialUser(email, password, user) {
  /**
   * @return the OverviewUser with ConfirmationRequest. No matter what the state the user is in
   * a new confirmation request can always be generated. User must be saved before change takes
   * effect.
   */
  def requestConfirmation: OverviewUser with ConfirmationRequest =
    OverviewUser.prepareNewRegistration(email, password, emailSubscriber)
}

object PotentialNewUser {
  def apply(email: String, password: String, emailSubscriber: Boolean): PotentialNewUser =
    new PotentialNewUser(email, password, emailSubscriber, OverviewUser.findByEmail(email))
}

/**
 * Helpers to get new or existing OverviewUsers
 */
object OverviewUser extends BlockingDatabaseProvider {
  import blockingDatabaseApi._

  private val TokenLength = 26
  val BcryptRounds = 7

  private def generateToken = scala.util.Random.alphanumeric.take(TokenLength).mkString
  private def generateTimestamp = new Timestamp(new Date().getTime())

  def findByEmail(email: String) : Option[OverviewUser] = {
    blockingDatabase.option(Users.filter(_.email === email)).map(OverviewUser.apply)
  }

  def findByResetPasswordTokenAndMinDate(token: String, minDate: Date): Option[OverviewUser with ResetPasswordRequest] = {
    blockingDatabase.option(
      Users
        .filter(_.resetPasswordToken === token)
        .filter(_.resetPasswordSentAt >= new java.sql.Timestamp(minDate.getTime))
    ).map(new UserWithResetPasswordRequest(_))
  }

  def findByConfirmationToken(token: String): Option[OverviewUser with ConfirmationRequest] = {
    blockingDatabase.option(
      Users
        .filter(_.confirmationToken === token)
    ).map(new UnconfirmedUser(_))
  }

  def prepareNewRegistration(email: String, password: String, emailSubscriber: Boolean): OverviewUser with ConfirmationRequest = {
    val confirmationToken = generateToken
    val confirmationSentAt = generateTimestamp

    val user = User(
      email = email,
      passwordHash = password.bcrypt(BcryptRounds),
      emailSubscriber = emailSubscriber,
      confirmationToken = Some(confirmationToken),
      confirmationSentAt = Some(confirmationSentAt)
    )
    new UnconfirmedUser(user)
  }

  private def apply(userData: Option[User]): Option[OverviewUser] = {
    userData.map(new OverviewUserImpl(_))
  }

  def apply(user: User): OverviewUser = new OverviewUserImpl(user)

  /**
   * Underlying implementation that manages the User object that is the conduit to the
   * database. As the user state is transformed, the underlying User is modified and
   * passed along
   */
  private case class OverviewUserImpl(user: User) extends OverviewUser {
    override def toUser = user
    override val id = user.id
    override val email = user.email
    override val requestedEmailSubscription = user.emailSubscriber
    override val treeTooltipsEnabled = user.treeTooltipsEnabled

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
        resetPasswordSentAt = Some(generateTimestamp)))
    }

    def asConfirmed: Option[OverviewUser with Confirmation] = {
      user.confirmedAt.map(d => new ConfirmedUser(user, d))
    }

    def withEmail(email: String): OverviewUser = {
      new OverviewUserImpl(user.copy(email = email))
    }

    def isAdministrator = user.role == UserRole.Administrator
  }

  /**
   * A User with an active confirmation request
   */
  private class UnconfirmedUser(user: User) extends OverviewUserImpl(user) with ConfirmationRequest {
    override val confirmationToken = user.confirmationToken.get
    override val confirmationSentAt = user.confirmationSentAt.get

    override def confirm: OverviewUser = {
      OverviewUserImpl(user.copy(
        confirmationToken = None,
        confirmedAt = Some(generateTimestamp)
      ))
    }
  }

  /**
   * A User who has confirmed
   */
  private class ConfirmedUser(user: User, val confirmedAt: Date) extends OverviewUserImpl(user) with Confirmation {
  }

  private class UserWithResetPasswordRequest(user: User) extends OverviewUserImpl(user) with ResetPasswordRequest {
    override val resetPasswordToken = user.resetPasswordToken.getOrElse(throw new Exception("logic"))
    override val resetPasswordSentAt = user.resetPasswordSentAt.getOrElse(throw new Exception("logic"))

    override def withNewPassword(password: String): OverviewUser = {
      OverviewUserImpl(user.copy(
        resetPasswordToken = None,
        resetPasswordSentAt = None,
        passwordHash = password.bcrypt(BcryptRounds)))
    }
  }
}
