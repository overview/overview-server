package steps

import org.fluentlenium.core.filter.Filter
import org.fluentlenium.core.filter.FilterConstructor.withText

import cucumber.runtime.scala.Transform.{t2Int, t2String}

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
    browser.findFirst("#tree-app-document-cursor h2").getText must beEqualTo(title)
    val divClass = docType match {
      case "Facebook" => "type-facebook"
      case "Twitter" => "type-twitter"
      case "DocumentCloud" => "type-documentcloud"
      case "secure" => "type-secure"
      case "insecure" => "type-insecure"
      case _ => throw new AssertionError("Unknown docType %s".format(docType))
    }
    Option(browser.findFirst("#tree-app-document-cursor div.%s".format(divClass))) must beSome
  }

  Then("""^I should see the tag "([^"]*)"$""") { (tagName: String) =>
    Framework.browser.find(".tag-name", withText(tagName)).size() must equalTo(1)
  }

  Then("""^tag "([^"]*)" should have (\d+) documents$""") { (tagName: String, count: Int) =>
    browser.find(".tag-name", withText(tagName)).click()
    CommonSteps.waitForAjaxToComplete
    val t = browser.find("#document-list-title").getText()
    t must startWith(s"$count documents")
  }

}

object DocumentSetShowSteps {
  private def browser = Framework.browser

  def clickDocument(title: String): Unit = {
    val elem = browser.findFirst("#document-list h3", withText(title))
    elem.click()
  }
}
