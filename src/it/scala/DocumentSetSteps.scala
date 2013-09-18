package steps

import org.fluentlenium.core.filter.FilterConstructor.withText

import controllers.routes
import models.OverviewDatabase
import org.overviewproject.tree.orm.DocumentSetUser
import models.orm.finders.{ DocumentSetFinder, DocumentSetUserFinder }
import models.orm.stores.{ DocumentSetStore, DocumentSetUserStore }
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.DocumentSet

class DocumentSetSteps extends BaseSteps {
  Given("""^there is a (public |)document set "([^"]*)" owned by "([^"]*)"$""") { (isPublic:String, title:String, ownerEmail:String) =>
    DocumentSetSteps.createDocumentSet(title, ownerEmail, isPublic=isPublic.length > 0)
  }

  Given("""^"([^"]*)" is allowed to view the document set "([^"]*)"$"""){ (email:String, title:String) =>
    DocumentSetSteps.ensureDocumentSetUser(email, title, Ownership.Viewer)
  }

  When("""^I browse to the document sets page$"""){ () =>
    browser.goTo(routes.DocumentSetController.index(0).url)
  }

  When("""^I browse to the "([^"]*)" document set$"""){ (arg0:String) =>
    throw new PendingException()
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
    CommonSteps.clickElement("a", "Import an example document set")
    CommonSteps.waitForAjaxToComplete
    CommonSteps.waitForAnimationsToComplete
    browser.findFirst("#import-public-document-sets form button").click()
  }

  When("""^I clone the shared document set "([^"]*)"$"""){ (title:String) =>
    browser.goTo(routes.DocumentSetController.index(0).url)
    CommonSteps.clickElement("a", "Import your documents")
    CommonSteps.clickElement("a", "Import documents shared with you")
    CommonSteps.waitForAjaxToComplete
    CommonSteps.waitForAnimationsToComplete
    browser.findFirst("#import-shared-document-sets form button").click()
  }

  Then("""^"([^"]*)" should (not |)be allowed to view the document set "([^"]*)"$"""){ (not: String, user:String, title:String) =>
    val dsu = DocumentSetSteps.getDocumentSetUser(user, title)
    if (not.length > 0) {
      dsu must beNone
    } else {
      dsu must beSome((dsu: DocumentSetUser) => dsu.role must beEqualTo(Ownership.Viewer))
    }
  }

  Then("""^I should see a clone button for the (example|shared) document set "([^"]*)"$"""){ (sharedOrExample:String, title:String) =>
    val id = sharedOrExample match {
      case "shared" => "import-shared-document-sets"
      case "example" => "import-public-document-sets"
    }
    val li = browser.findFirst("#%s li".format(id), withText.contains(title))
    val button = li.findFirst("button", withText.contains("Clone"))
    Option(button) must beSome
  }

  Then("""^I should see a document set "([^"]*)"$""") { (title:String) =>
    val documentSet = DocumentSetSteps.getDocumentSet(title)
    Option(browser.findFirst(".document-sets h3", withText(title))) must beSome
  }

  Then("""^the document set "([^"]*)" should be public$"""){ (title:String) =>
    val documentSet = OverviewDatabase.inTransaction { DocumentSetFinder.byTitle(title).headOption }.getOrElse(throw new AssertionError("Document set does not exist"))
    documentSet.isPublic must beTrue
  }
}

object DocumentSetSteps {
  def createDocumentSet(title: String, ownerEmail: String, isPublic : Boolean = false) = {
    CommonSteps.ensureUser(ownerEmail)
    OverviewDatabase.inTransaction {
      val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(
        query=Some("query"),
        title=title,
        isPublic=isPublic
      ))
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet, ownerEmail, Ownership.Owner))
    }
  }

  /** Returns a DocumentSet.
    *
    * @throws an AssertionError if the document set does not exist.
    */
  def getDocumentSet(title: String) = {
    OverviewDatabase.inTransaction {
      DocumentSetFinder.byTitle(title).headOption.getOrElse(throw new AssertionError("Document set does not exist"))
    }
  }

  /** Returns an Option[DocumentSetUser] */
  def getDocumentSetUser(email: String, documentSetTitle: String) = {
    val documentSet = getDocumentSet(documentSetTitle)
    OverviewDatabase.inTransaction {
      DocumentSetUserFinder.byDocumentSetAndUser(documentSet, email).headOption
    }
  }

  /** Ensures a DocumentSetUser exists */
  def ensureDocumentSetUser(email: String, documentSetTitle: String, ownership: Ownership.Value) = {
    val documentSet = getDocumentSet(documentSetTitle)
    OverviewDatabase.inTransaction {
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSet, email, ownership))
    }
  }
}
