package steps

import com.icegreen.greenmail.util.GreenMailUtil
import java.util.Date
import java.sql.Timestamp
import javax.mail.internet.InternetAddress
import com.github.t3hnar.bcrypt._

import models.OverviewDatabase
import models.orm.User
import models.orm.finders.UserFinder
import models.orm.stores.UserStore
import controllers.routes

class RegistrationSteps extends BaseSteps {
  Given("""^there is a user "([^"]*)" with password "([^"]*)" and confirmation token "([^"]*)"$""") { (email:String, password:String, token:String) =>
    RegistrationSteps.createUserWithConfirmation(email, password, token)
  }

  Given("""^there is a user "([^"]*)" with password "([^"]*)" and reset-password token "([^"]*)"$""") { (email:String, password:String, token:String) =>
    RegistrationSteps.createUserWithReset(email, password, token)
  }

  When("""^I register with email "([^"]*)" and password "([^"]*)"$""") { (email:String, password:String) =>
    RegistrationSteps.register(email, password)
  }

  When("""^I confirm registration with token "([^"]*)"$""") { (token:String) =>
    val url = Framework.routeToUrl(routes.ConfirmationController.show(token))
    browser.goTo(url)
  }

  When("""^I request a password reset for "([^"]*)"$""") { (email:String) =>
    RegistrationSteps.requestReset(email)
  }

  When("""^I browse to the reset-password page with token "([^"]*)"$""") { (token:String) =>
    val url = Framework.routeToUrl(routes.PasswordController.edit(token))
    browser.goTo(url)
  }

  When("""^I reset the password to "([^"]*)"$"""){ (password:String) =>
    browser.$("input[name=password]").text(password)
    browser.$("input[name=password2]").text(password)
    browser.$("input[type=submit]").click()
  }

  Then("""^"([^"]*)" should receive an email with a confirmation token$"""){ (email:String) =>
    mailServer.waitForIncomingEmail(1) must beTrue
    val message = mailServer.getReceivedMessages()(0)

    message.getAllRecipients()(0) must beEqualTo(new InternetAddress(email))

    val body = GreenMailUtil.getBody(message)
    body must contain(routes.ConfirmationController.show("").toString)
  }

  Then("""^user "([^"]*)" should be confirmed$"""){ (email:String) =>
    OverviewDatabase.inTransaction {
      val user = UserFinder.byEmail(email)
      user.headOption.flatMap(_.confirmedAt) must beSome
    }
  }

  Then("""^"([^"]*)" should receive an email with a reset-password token$""") { (email:String) =>
    mailServer.waitForIncomingEmail(1) must beTrue
    val message = mailServer.getReceivedMessages()(0)

    message.getAllRecipients()(0) must beEqualTo(new InternetAddress(email))

    val body = GreenMailUtil.getBody(message)
    body must contain(routes.PasswordController.edit("").toString)
  }

  Then("""^"([^"]*)" should receive an email without a reset-password token$""") { (email:String) =>
    mailServer.waitForIncomingEmail(1) must beTrue
    val message = mailServer.getReceivedMessages()(0)

    message.getAllRecipients()(0) must beEqualTo(new InternetAddress(email))

    val body = GreenMailUtil.getBody(message)
    body must not contain(routes.PasswordController.edit("").toString)
  }

  Then("""^"([^"]*)" should have a password-reset token""") { (email: String) =>
    OverviewDatabase.inTransaction {
      val user = UserFinder.byEmail(email)
      user.headOption.flatMap(_.resetPasswordToken) must beSome
    }
  }

  Then("""^"([^"]*)" should have password "([^"]*)"$""") { (email:String, password:String) =>
    OverviewDatabase.inTransaction {
      val user = UserFinder.byEmail(email).headOption
      user must beSome
      user.map { u =>
        password.isBcrypted(u.passwordHash) must beTrue
      }
    }
  }
}

object RegistrationSteps {
  private def browser = Framework.browser

  def createUserWithConfirmation(email: String, password: String, token: String) : User = {
    val passwordHash = password.bcrypt(4) // least CPU possible
    val user = User(
      email=email,
      passwordHash=passwordHash,
      confirmationToken=Some(token),
      confirmationSentAt=Some(new Timestamp(new Date().getTime()))
    )
    OverviewDatabase.inTransaction { UserStore.insertOrUpdate(user) }
  }

  def createUserWithReset(email: String, password: String, token: String) : User = {
    val user = CommonSteps.createUser(email, password)
    val resetUser = user.copy(
      resetPasswordToken = Some(token),
      resetPasswordSentAt = Some(new Timestamp(new Date().getTime()))
    )
    OverviewDatabase.inTransaction { UserStore.insertOrUpdate(resetUser) }
  }

  def register(email: String, password: String) = {
    val url = Framework.routeToUrl(routes.WelcomeController.show)
    browser.goTo(url)

    browser.$(".user-form input[name=email]").text(email)
    browser.$(".user-form input[name=password]").text(password)
    browser.$(".user-form input[name=password2]").text(password)
    browser.$(".user-form input[type=submit]").click()
  }

  def requestReset(email: String) = {
    val url = Framework.routeToUrl(routes.WelcomeController.show)
    browser.goTo(url)
    browser.$(".forgot-password a").click()

    browser.$("input[name=email]").text(email)
    browser.$("input[type=submit]").click()
  }
}
