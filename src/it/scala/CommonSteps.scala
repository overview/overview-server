package steps

import java.sql.Timestamp
import java.util.Date
import java.util.concurrent.TimeUnit
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.{ JavascriptExecutor, WebDriver }
import ua.t3hnar.bcrypt._

import models.OverviewDatabase
import models.orm.User
import models.orm.finders.{ DocumentSetCreationJobFinder, UserFinder }
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
    // do nothing. By default, users aren't logged in.
  }

  When("""^I browse to the welcome page$"""){ () =>
    val url = Framework.routeToUrl(routes.WelcomeController.show)

    browser.goTo(url)
  }

  When("""^I wait for all AJAX requests to complete$"""){ () =>
    CommonSteps.waitForAjaxToComplete
  }

  When("""^I wait for all animations to complete$"""){ () =>
    CommonSteps.waitForAnimationsToComplete
  }

  When("""^I wait for all jobs to complete$"""){ () =>
    CommonSteps.waitForJobsToComplete
  }
}

object CommonSteps {
  private def browser = Framework.browser

  def createUser(email: String, password: String) : User = {
    val passwordHash = password.bcrypt(4) // least CPU possible
    val user = User(email=email, passwordHash=passwordHash, confirmedAt=Some(new Timestamp(new Date().getTime())))
    OverviewDatabase.inTransaction { UserStore.insertOrUpdate(user) }
  }

  def ensureUser(email: String): User = {
    val existingUser = OverviewDatabase.inTransaction { UserFinder.byEmail(email).headOption }
    existingUser.getOrElse(createUser(email, email))
  }

  def logIn(email: String, password: String) = {
    val url = Framework.routeToUrl(routes.SessionController.new_)
    browser.goTo(url)
    browser.$(".session-form input[name=email]").text(email)
    browser.$(".session-form input[name=password]").text(password)
    browser.$(".session-form input[type=submit]").click()
  }

  def logOut = {
    val url = Framework.routeToUrl(routes.SessionController.delete)
    browser.goTo(url)
  }

  def createAndLogInAsUser(email: String, password: String) = {
    createUser(email, password)
    logIn(email, password)
  }

  private def waitForBoolean(timeoutInSeconds: Int)(f: WebDriver => Boolean) = {
    new WebDriverWait(browser.webDriver, timeoutInSeconds)
      .until(new ExpectedCondition[java.lang.Boolean] {
        def apply(driver: WebDriver) : java.lang.Boolean = {
          if (f(driver)) {
            java.lang.Boolean.TRUE
          } else {
            java.lang.Boolean.FALSE
          }
        }
      })
  }

  private def waitForJavascriptBoolean(script: String) = {
    val timeout = 5 // seconds
    /*
     * Normally, we'd just check for, say, "jQuery.active == 0". However, we
     * sometimes create JavaScript requests on a delay--in a window.setTimeout
     * for instance.
     *
     * The workaround: check a few times in a row. If our test succeeds several
     * times, we're probably okay.
     */
    var nSuccesses = 0
    val neededSuccesses = 2

    waitForBoolean(5) { (driver: WebDriver) =>
      val executor = driver.asInstanceOf[JavascriptExecutor]
      val ret = executor.executeScript(script).asInstanceOf[java.lang.Boolean];
      if (ret) {
        // If JS evaluates to true, we might be succeeding
        nSuccesses += 1
        nSuccesses >= neededSuccesses
      } else {
        // Otherwise, restart our counter
        nSuccesses = 0
        false
      }
    }
  }

  def waitForJQuery = {
    waitForJavascriptBoolean("return jQuery && jQuery.isReady;")
  }

  def waitForAjaxToComplete = {
    waitForJQuery
    waitForJavascriptBoolean("return jQuery.active == 0;")
  }

  def waitForAnimationsToComplete = {
    waitForJavascriptBoolean("return jQuery(':animated').length == 0;")
  }

  def waitForJobsToComplete = {
    val timeoutInSeconds = 60

    waitForBoolean(60) { (_: WebDriver) =>
      OverviewDatabase.inTransaction {
        DocumentSetCreationJobFinder.all.count == 0
      }
    }
  }
}
