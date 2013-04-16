package steps

import org.fluentlenium.core.filter.FilterConstructor.withText

import controllers.routes
import models.OverviewDatabase
import models.orm.{ DocumentSet, DocumentSetType, DocumentSetUser }
import models.orm.finders.DocumentSetFinder
import models.orm.stores.{ DocumentSetStore, DocumentSetUserStore }
import org.overviewproject.tree.Ownership

class DocumentSetSteps extends BaseSteps {
  Given("""^there is a (public |)document set "([^"]*)" owned by "([^"]*)"$""") { (isPublic:String, title:String, ownerEmail:String) =>
    DocumentSetSteps.createDocumentSet(title, ownerEmail, isPublic=isPublic.length > 0)
  }

  When("""^I browse to the document sets page$"""){ () =>
    browser.goTo(routes.DocumentSetController.index(0).url)
  }

  When("""^I open the share dialog for "([^"]*)"$"""){ (title:String) =>
    val li = browser.findFirst(".document-sets li", withText.contains(title))
    val a = li.findFirst("a.show-sharing-settings")
    a.click()
    CommonSteps.waitForAjaxToComplete
    CommonSteps.waitForAnimationsToComplete
  }

  When("""^I clone the example document set "([^"]*)"$"""){ (title:String) =>
    browser.goTo(routes.DocumentSetController.index(0).url)
    CommonSteps.waitForAjaxToComplete
    CommonSteps.waitForAnimationsToComplete
    browser.findFirst("#import-public-document-sets form button").click()
  }

  Then("""^I should see a clone button for the example document set "([^"]*)"$"""){ (title:String) =>
    val documentSet = OverviewDatabase.inTransaction { DocumentSetFinder.byTitle(title).headOption }.getOrElse(throw new AssertionError("Document set does not exist"))
    val cloneUrl = routes.DocumentSetController.createClone(documentSet).url
    val selector = "#import-public-document-sets form"
    Option(browser.findFirst(selector)).map(_.getAttribute("action")) must beSome.which(_.contains(cloneUrl))
  }

  Then("""^I should see a document set "([^"]*)"$""") { (title:String) =>
    val documentSet = OverviewDatabase.inTransaction { DocumentSetFinder.byTitle(title).headOption }.getOrElse(throw new AssertionError("Document set does not exist"))
    Option(browser.findFirst(".document-sets h2", withText(title))) must beSome
  }

  Then("""^the document set "([^"]*)" should be public$"""){ (title:String) =>
    val documentSet = OverviewDatabase.inTransaction {
      DocumentSetFinder.byTitle(title).headOption.getOrElse(throw new AssertionError("Document set does not exist"))
    }
    documentSet.isPublic must beTrue
  }
}

object DocumentSetSteps {
  def createDocumentSet(title: String, ownerEmail: String, isPublic : Boolean = false) = {
    CommonSteps.ensureUser(ownerEmail)
    OverviewDatabase.inTransaction {
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(
        documentSetType=DocumentSetType.DocumentCloudDocumentSet,
        query=Some("query"),
        title=title,
        isPublic=isPublic
      ))
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet, ownerEmail, Ownership.Owner))
    }
  }
}
