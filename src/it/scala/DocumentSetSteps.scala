package steps

import org.fluentlenium.core.filter.FilterConstructor.withText

import controllers.routes
import models.OverviewDatabase
import models.orm.{ DocumentSet, DocumentSetType, DocumentSetUser }
import models.orm.finders.DocumentSetFinder
import models.orm.stores.{ DocumentSetStore, DocumentSetUserStore }
import org.overviewproject.tree.Ownership

class DocumentSetSteps extends BaseSteps {
  Given("""^there is a public document set "([^"]*)" owned by "([^"]*)"$""") { (title:String, ownerEmail:String) =>
    DocumentSetSteps.createPublicDocumentSet(title, ownerEmail)
  }

  When("""^I browse to the document sets page$"""){ () =>
    browser.goTo(routes.DocumentSetController.index(0).url)
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
}

object DocumentSetSteps {
  def createPublicDocumentSet(title: String, ownerEmail: String) = {
    CommonSteps.ensureUser(ownerEmail)
    OverviewDatabase.inTransaction {
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(
        documentSetType=DocumentSetType.DocumentCloudDocumentSet,
        query=Some("query"),
        title=title,
        isPublic=true
      ))
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet, ownerEmail, Ownership.Owner))
    }
  }
}
