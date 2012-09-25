package models

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

  def passwordMatches(password: String): Boolean

  /** @return None if the user has no open confirmation request */
  def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest]

  def save: Unit
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

  def findById(id: Long): Option[OverviewUser] = create(User.findById(id))

  def findByEmail(email: String): Option[OverviewUser] = create(User.findByEmail(email))

  def findByConfirmationToken(token: String): Option[OverviewUser with ConfirmationRequest] = {
    val user = User.findByConfirmationToken(token)
    user.map(new UnconfirmedUser(_))
  }

  def prepareNewRegistration(email: String, password: String): OverviewUser with ConfirmationRequest = {
    val TokenLength = 26
    val BcryptRounds = 7
    val confirmationToken = scala.util.Random.alphanumeric take (TokenLength) mkString;
    val confirmationSentAt = new Timestamp(now().getMillis())

    val user = User(email = email, passwordHash = password.bcrypt(BcryptRounds),
      confirmationToken = Some(confirmationToken),
      confirmationSentAt = Some(confirmationSentAt))
    new UnconfirmedUser(user)
  }

  private def create(userData: Option[User]): Option[OverviewUser] = {
    userData.map(new OverviewUserImpl(_))
  }

  /**
   * Underlying implementation that manages the User object that is the conduit to the
   * database. As the user state is transformed, the underlying User is modified and
   * passed along
   */
  private class OverviewUserImpl(user: User) extends OverviewUser {
    val id = user.id
    val email = user.email

    def passwordMatches(password: String): Boolean = {
      password.isBcrypted(user.passwordHash)
    }

    def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest] = {
      if (user.confirmationToken.isDefined) Some(new UnconfirmedUser(user))
      else None
    }

    def save: Unit = user.save
  }

  /**
   * A User with an active confirmation request
   */
  private class UnconfirmedUser(user: User) extends OverviewUserImpl(user) with ConfirmationRequest {
    val confirmationToken = user.confirmationToken.get
    val confirmationSentAt = user.confirmationSentAt.get

    def confirm: OverviewUser = {
      user.confirmationToken = None
      user.confirmedAt = Some(new Timestamp(now().getMillis))

      this
    }
  }

}
