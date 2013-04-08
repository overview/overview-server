package steps

class LoginSteps extends BaseSteps {
  When("""^I log in with email "([^"]*)" and password "([^"]*)"$""") { (email: String, password: String) =>
    CommonSteps.logIn(email, password)
  }

  Then("""^I should be logged in as "([^"]*)"$"""){ (email: String) =>
    val html = Option(browser.findFirst(".logged-in strong")).map(_.getText).getOrElse("")
    html must contain(email)
  }
}
