package steps

import cucumber.api.scala.{EN, ScalaDsl}
import org.specs2.matcher.JUnitMustMatchers

trait BaseSteps extends ScalaDsl with EN with JUnitMustMatchers {
  /** Accesses the test web browser, through FluentLenium.
    *
    * Use it like this:
    *
    *   browser.$("input[type=submit]").click()
    */
  protected def browser = Framework.browser

  /** Accesses the test SMTP server, through GreenMail.
    *
    * Use it like this:
    *
    *   mailServer.waitForIncomingEmail(1) must beTrue
    *   val message = mailServer.getReceivedMessages()(0)
    *   val body = GreenMailUtil.getBody(message)
    *   body mustContain("a certain string")
    */
  protected def mailServer = Framework.mailServer
}
