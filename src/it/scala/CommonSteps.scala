package steps

import java.sql.Timestamp
import java.util.Date
import java.util.concurrent.TimeUnit
import org.fluentlenium.core.filter.Filter
import org.fluentlenium.core.filter.FilterConstructor.{ withName, withText }
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.{ JavascriptExecutor, WebDriver }
import com.github.t3hnar.bcrypt._

import models.OverviewDatabase
import models.orm.{User,UserRole}
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

  When("""^I wait for all file operations to complete$""") { () =>
    CommonSteps.waitForFileReadersToComplete
  }

  When("""^I wait for all animations to complete$"""){ () =>
    CommonSteps.waitForAnimationsToComplete
  }

  When("""^I wait for all jobs to complete$"""){ () =>
    CommonSteps.waitForJobsToComplete
  }

  When("""^I choose the file "([^"]*)"$"""){ (filename:String) =>
    CommonSteps.chooseFile(filename)
  }
      

  When("""^I click the "([^"]*)" (checkbox|link|button)$"""){ (label:String, elementType:String) =>
    val elementName = elementType match {
      case "link" => "a"
      case "checkbox" => "label" // Clicking the label for a checkbox has the same effect
      case s: String => s
    }
    CommonSteps.clickElement(elementName, label)
  }

  When("""^I click "([^"]*)" in the "([^"]*)" dropdown"""){ (link:String, menu: String) =>
    CommonSteps.clickElement("a", menu)
    CommonSteps.clickElement("a", link)
  }

  When("""^I enter an? (\w+) of "([^"]*)"$"""){ (name:String, text:String) =>
    val elems = browser.$("input", withName(name))
    elems.text(text)
  }

  When("""^I hover over the list item "([^"]*)"$"""){ (text:String) =>
    val elem = browser.findFirst("li", withText.contains(text))
    new Actions(browser.getDriver)
      .moveToElement(elem.getElement)
      .perform
  }

  Then("""^I should (not |)see an? (enabled |)"([^"]*)" (button)$"""){ (notOrNothing:String, activeOrNothing:String, label:String, name:String) =>
    val positive : Boolean = notOrNothing.length == 0
    val elem = CommonSteps.findElement(name, label)
    if (positive) {
      Option(elem).isDefined must beTrue
      if (activeOrNothing.length > 0) {
        elem.isEnabled must beTrue
      }
    } else {
      Option(elem).fold(false) { e =>
        if (activeOrNothing.length > 0) e.isEnabled else false
      } must beFalse
    }
  }

  Then("""^I should not see an? "([^"]*)" checkbox$"""){ (label:String) =>
    val elems = browser.$("label", withText.contains(label))
    elems.size must beEqualTo(0)
  }

  Then("""^I should see a progress bar$"""){ () =>
    var elem = browser.findFirst("progress")
    Option(elem) must beSome
  }

  Then("""^the progress bar should finish$"""){ () =>
    CommonSteps.waitForBoolean(10) { driver: WebDriver =>
      val elem = browser.findFirst("progress")
      val value = elem.getAttribute("value").toDouble
      val max = elem.getAttribute("max").toDouble
      value == max
    }
  }

  Then("""^I should be redirected to the document set index$"""){ () =>
    throw new PendingException()
  }
}

object CommonSteps {
  private def browser = Framework.browser

  def createUser(email: String, password: String) : User = {
    val passwordHash = password.bcrypt(4) // least CPU possible
    val user = User(
      email=email,
      passwordHash=passwordHash,
      role=if (email.contains("admin")) UserRole.Administrator else UserRole.NormalUser,
      confirmedAt=Some(new Timestamp(new Date().getTime()))
    )
    OverviewDatabase.inTransaction { UserStore.insertOrUpdate(user) }
  }

  def ensureUser(email: String, password: Option[String] = None): User = {
    val existingUser = OverviewDatabase.inTransaction { UserFinder.byEmail(email).headOption }
    existingUser.getOrElse{ createUser(email, password.getOrElse(email)) }
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
    ensureUser(email, Some(password))
    logIn(email, password)
  }

  def findElement(name: String, label: String) = {
    browser.findFirst(name, withText.contains(label))
  }

  /** Clicks an element with the given name and text.
    *
    * Example:
    *
    *   // clicks the first visible link that includes text "help"
    *   clickElement("a", "help")
    */
  def clickElement(name: String, label: String) = {
    findElement(name, label).click
  }

  /** Sets the first input[type=file] to the given file.
    *
    * Specify a file that is a resource. This function will copy the resource
    * to the filesystem; when the scenario finishes, the file will be deleted.
    */
  def chooseFile(resourcePath: String) = {
    val path = Framework.copyResourceToFileSomewhere(resourcePath).getAbsolutePath
    val input = browser.findFirst("input", new Filter("type", "file"))
    input.getElement.sendKeys(path)
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
    val timeoutInSeconds = 15

    waitForBoolean(timeoutInSeconds) { (driver: WebDriver) =>
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
    waitForJavascriptBoolean("return !!(window.jQuery && jQuery.isReady);")
  }

  def waitForFileReadersToComplete = {
    // There's no way to automate this, but file readers are generally quick
    Thread.sleep(500)
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

    waitForBoolean(timeoutInSeconds) { (_: WebDriver) =>
      OverviewDatabase.inTransaction {
        DocumentSetCreationJobFinder.all.count == 0
      }
    }
  }
}
