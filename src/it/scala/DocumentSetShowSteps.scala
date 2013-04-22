package steps

import anorm._
import org.fluentlenium.core.filter.Filter

import models.OverviewDatabase
import models.orm.DocumentSetUser
import models.orm.stores.DocumentSetUserStore
import controllers.routes
import org.overviewproject.tree.Ownership
import org.overviewproject.test.DbSetup

class DocumentSetShowSteps extends BaseSteps {
  private var documentSetId = -1L

  private def documentSetUrl(documentSetId: Long) : String = {
    Framework.routeToUrl(routes.DocumentSetController.show(documentSetId))
  }

  private def documentSetUrl() : String = {
    documentSetUrl(documentSetId)
  }

  Given("""^there is a basic document set owned by "([^"]*)"$""") { (email: String) =>
    OverviewDatabase.inTransaction {
      documentSetId = DocumentSetShowSteps.createBasicDocumentSet()
      DocumentSetUserStore.insertOrUpdate(DocumentSetUser(
        documentSetId, email, Ownership.Owner
      ))
    }
  }

  When("""^I browse to the document set$"""){ () =>
    val url = documentSetUrl()
    Framework.browser.goTo(url)
    CommonSteps.waitForAjaxToComplete
  }

  Then("""^I should see the tree$"""){ () =>
    Framework.browser.$("#tree").size() must equalTo(1)
  }

  Then("""^I should see the focus slider$"""){ () =>
    Framework.browser.$("#focus").size() must equalTo(1)
  }

  Then("""^I should see the document list$"""){ () =>
    Framework.browser.$("#document-list").size() must equalTo(1)
  }

  Then("""^I should see the (Facebook|Twitter|DocumentCloud|secure|insecure) document "([^"]*)"$"""){ (docType:String, title:String) =>
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
}

object DocumentSetShowSteps {
  private def browser = Framework.browser

  def createBasicDocumentSet(title: String = "basic") : Long = {
    implicit val connection = OverviewDatabase.currentConnection

    val documentSetId = DbSetup.insertDocumentSet(title)
    val nodeSql = SQL("INSERT INTO node (id, document_set_id, description, parent_id) VALUES ({id}, {document_set_id}, {description}, {parent_id})")
    val node1Id = nodeSql.on('id -> 1L, 'document_set_id -> documentSetId, 'description -> "node 1", 'parent_id -> None).executeInsert()
    val node2Id = nodeSql.on('id -> 2L, 'document_set_id -> documentSetId, 'description -> "node 2", 'parent_id -> node1Id).executeInsert()
    val node3Id = nodeSql.on('id -> 3L, 'document_set_id -> documentSetId, 'description -> "node 3", 'parent_id -> node1Id).executeInsert()
    val documentSql = SQL("INSERT INTO document (id, type, document_set_id, title, description, text) VALUES ({id}, 'CsvImportDocument', {document_set_id}, {title}, 'description', 'text')")
    val document1Id = documentSql.on('id -> 1L, 'document_set_id -> documentSetId, 'title -> "document 1").executeInsert()
    val document2Id = documentSql.on('id -> 2L, 'document_set_id -> documentSetId, 'title -> "document 2").executeInsert()
    val document3Id = documentSql.on('id -> 3L, 'document_set_id -> documentSetId, 'title -> "document 3").executeInsert()
    val tieNodeToDocumentSql = SQL("INSERT INTO node_document (node_id, document_id) VALUES ({node_id}, {document_id})")
    tieNodeToDocumentSql.on('node_id -> node1Id, 'document_id -> document1Id).execute()
    tieNodeToDocumentSql.on('node_id -> node1Id, 'document_id -> document2Id).execute()
    tieNodeToDocumentSql.on('node_id -> node1Id, 'document_id -> document3Id).execute()
    tieNodeToDocumentSql.on('node_id -> node2Id, 'document_id -> document1Id).execute()
    tieNodeToDocumentSql.on('node_id -> node3Id, 'document_id -> document2Id).execute()
    tieNodeToDocumentSql.on('node_id -> node3Id, 'document_id -> document3Id).execute()
    documentSetId
  }

  def clickDocument(title: String) : Unit = {
    val elem = browser.findFirst("#document-list a", new Filter("title", title))
    elem.click()
  }
}
