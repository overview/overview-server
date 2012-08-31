package models

import java.sql.Timestamp
import models.orm.User
import org.joda.time.DateTime.now
import ua.t3hnar.bcrypt._

trait OverviewUser {
  val id: Long
  val email: String
  
  def passwordMatches(password: String): Boolean
  def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest]
  
  def save: Unit
}

trait ConfirmationRequest {
  val confirmationToken: String
  val confirmationSentAt: Timestamp
  
  def confirm: OverviewUser
}

case class PotentialUser(val email: String, val password: String) {
  private val user = OverviewUser.findByEmail(email)
  
  def withValidCredentials: Option[OverviewUser] = {
    user.find(u => u.passwordMatches(password))
  }
  
  def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest] = {
    user match {
      case Some(u) => u.withConfirmationRequest
      case None => None
    }
  }
  
  def requestConfirmation: OverviewUser with ConfirmationRequest = 
    OverviewUser.prepareNewRegistration(email, password)

}

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
    val confirmationToken = scala.util.Random.alphanumeric take(TokenLength) mkString;
    val confirmationSentAt = new Timestamp(now().getMillis())
      
    val user = User(email = email, passwordHash = password.bcrypt(BcryptRounds),
                    confirmationToken = Some(confirmationToken),
                    confirmationSentAt = Some(confirmationSentAt))
    new UnconfirmedUser(user)
  }
  
  private def create(userData: Option[User]): Option[OverviewUser] = {
    userData.map(new OverviewUserImpl(_))
  }
  
  private class UnconfirmedUser(user: User) extends OverviewUserImpl(user) with ConfirmationRequest {
    val confirmationToken = user.confirmationToken.get
    val confirmationSentAt = user.confirmationSentAt.get
          
    def confirm: OverviewUser = {
      user.confirmationToken = None
      user.confirmedAt = Some(new Timestamp(now().getMillis))
          
      this
    }
  }
  
  private class OverviewUserImpl(user: User) extends OverviewUser {
    val id = user.id
    val email = user.email
    
    def passwordMatches(password: String): Boolean = {
      password.isBcrypted(user.passwordHash)
    }
    
    def withConfirmationRequest: Option[OverviewUser with ConfirmationRequest] = {
      if (user.confirmationToken.isDefined) {
        Some(new UnconfirmedUser(user))
      }
      else None
    }
    
    def save: Unit = user.save
  }
}