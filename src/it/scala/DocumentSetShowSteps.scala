package steps

import scala.collection.JavaConverters._

import anorm._
import org.fluentlenium.core.filter.Filter
import org.fluentlenium.core.filter.FilterConstructor.withText

import models.OverviewDatabase
import models.orm.DocumentSetUser
import models.orm.stores.DocumentSetUserStore
import controllers.routes
import org.overviewproject.tree.Ownership
import org.overviewproject.test.DbSetup

class DocumentSetShowSteps extends BaseSteps {
  Then("""^I should see the tree$""") { () =>
    Framework.browser.$("#tree").size() must equalTo(1)
  }

  Then("""^I should see the focus slider$""") { () =>
    Framework.browser.$("#focus").size() must equalTo(1)
  }

  Then("""^I should see the document list$""") { () =>
    Framework.browser.$("#document-list").size() must equalTo(1)
  }

  Then("""^I should see the (Facebook|Twitter|DocumentCloud|secure|insecure) document "([^"]*)"$""") { (docType: String, title: String) =>
    DocumentSetShowSteps.clickDocument(title)
    CommonSteps.waitForAjaxToComplete
    val iframe = browser.findFirst("#document iframe").getElement
    browser.webDriver.switchTo.frame(iframe)
    browser.findFirst("h3").getText must beEqualTo(title)
    val divClass = docType match {
      case "Facebook" => "type-facebook"
      case "Twitter" => "type-twitter"
      case "DocumentCloud" => "type-documentcloud"
      case "secure" => "type-secure"
      case "insecure" => "type-insecure"
      case _ => throw new AssertionError("Unknown docType %s".format(docType))
    }
    Option(browser.findFirst("div.%s".format(divClass))) must beSome
    browser.webDriver.switchTo.defaultContent
  }

  Then("""^I should see the tag "([^"]*)"$""") { (tagName: String) =>
    Framework.browser.find(".tag-name", withText(tagName)).size() must equalTo(1)
  }

}

object DocumentSetShowSteps {
  private def browser = Framework.browser

  def clickDocument(title: String): Unit = {
    val elem = browser.findFirst("#document-list a", new Filter("title", title))
    elem.click()
  }
}
