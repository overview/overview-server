package steps

import cucumber.api.scala.{EN, ScalaDsl}
import org.specs2.matcher.JUnitMustMatchers

trait BaseSteps extends ScalaDsl with EN with JUnitMustMatchers {
  protected def browser = Framework.browser
}
