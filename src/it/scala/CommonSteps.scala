package steps

import java.util.Date
import java.sql.Timestamp
import ua.t3hnar.bcrypt._

import models.OverviewDatabase
import models.orm.User
import models.orm.stores.UserStore
import controllers.routes

class CommonSteps extends BaseSteps {
  Given("""^there is a user "([^"]*)" with password "([^"]*)"$""") { (email: String, password: String) =>
    CommonSteps.createUser(email, password)
  }

  Given("""^I am logged in as "([^"]*)"$"""){ (email: String) =>
    CommonSteps.createAndLogInAsUser(email, email)
  }

  Given("""^I am not logged in$"""){ () =>
    // do nothing (yet)
  }

  When("""^I browse to the welcome page$"""){ () =>
    val url = Framework.routeToUrl(routes.WelcomeController.show)

    browser.goTo(url)
  }
}

object CommonSteps {
  private def browser = Framework.browser

  def createUser(email: String, password: String) : User = {
    val passwordHash = password.bcrypt(4) // least CPU possible
    val user = User(email=email, passwordHash=passwordHash, confirmedAt=Some(new Timestamp(new Date().getTime())))
    OverviewDatabase.inTransaction { UserStore.insertOrUpdate(user) }
  }

  def logIn(email: String, password: String) = {
    val url = Framework.routeToUrl(routes.SessionController.new_)
    browser.goTo(url)
    browser.$(".session-form input[name=email]").text(email)
    browser.$(".session-form input[name=password]").text(password)
    browser.$(".session-form input[type=submit]").click()
  }

  def createAndLogInAsUser(email: String, password: String) = {
    createUser(email, password)
    logIn(email, password)
  }
}
