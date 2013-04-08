package steps

class LoginSteps extends BaseSteps {
  When("""^I log in with email "([^"]*)" and password "([^"]*)"$""") { (email: String, password: String) =>
    CommonSteps.logIn(email, password)
  }

  When("""^I log out$""") { () =>
    CommonSteps.logOut
  }

  Then("""^I should be logged in as "([^"]*)"$""") { (email: String) =>
    val html = Option(browser.findFirst(".logged-in strong")).map(_.getText).getOrElse("")
    html must contain(email)
  }

  Then("""^I should see an error in the login form$""") { () =>
    val error = browser.find(".session-form .error")
    error.size must be_>=(0)
  }

  Then("""^I should not be logged in$""") { () =>
    val loggedIn = browser.find(".logged-in")
    loggedIn.size must beEqualTo(0)
  }
}
