package steps

import anorm._

import controllers.routes
import org.overviewproject.test.DbSetup

import cucumber.runtime.{EN, ScalaDsl, PendingException}
import org.specs2.matcher.MustMatchers

class DocumentSetShowSteps extends ScalaDsl with EN with MustMatchers {
  lazy implicit val browser = Framework.browser

  private var documentSetId = -1L

  private def documentSetUrl(documentSetId: Long) : String = {
    Framework.routeToUrl(routes.DocumentSetController.show(documentSetId))
  }

  private def documentSetUrl() : String = {
    documentSetUrl(documentSetId)
  }

  Given("""^there is a basic document set$""") { () =>
    documentSetId = DocumentSetShowSteps.createBasicDocumentSet()
  }

  Given("""^I am logged in$"""){ () =>
    // do nothing (yet)
  }

  When("""^I browse to the document set$"""){ () =>
    val url = documentSetUrl()
    browser.goTo(url)
  }

  Then("""^I should see the tree$"""){ () =>
    browser.$("#tree").size() must equalTo(1)
  }

  Then("""^I should see the focus slider$"""){ () =>
    browser.$("#focus").size() must equalTo(1)
  }

  Then("""^I should see the document list$"""){ () =>
    browser.$("#document-list").size() must equalTo(1)
  }

}

object DocumentSetShowSteps {
  def createBasicDocumentSet() : Long = {
    Framework.db { implicit connection =>
      SQL("TRUNCATE TABLE document_set CASCADE").execute()
      val documentSetId = DbSetup.insertDocumentSet("basic")
      val nodeSql = SQL("INSERT INTO node (document_set_id, description, parent_id) VALUES ({document_set_id}, {description}, {parent_id})")
      val node1Id = nodeSql.on('document_set_id -> documentSetId, 'description -> "node 1", 'parent_id -> None).executeInsert()
      val node2Id = nodeSql.on('document_set_id -> documentSetId, 'description -> "node 2", 'parent_id -> node1Id).executeInsert()
      val node3Id = nodeSql.on('document_set_id -> documentSetId, 'description -> "node 3", 'parent_id -> node1Id).executeInsert()
      val documentSql = SQL("INSERT INTO document (document_set_id, title, text_url, view_url) VALUES ({document_set_id}, {title}, 'text_url', 'view_url')")
      val document1Id = documentSql.on('document_set_id -> documentSetId, 'title -> "document 1").executeInsert()
      val document2Id = documentSql.on('document_set_id -> documentSetId, 'title -> "document 2").executeInsert()
      val document3Id = documentSql.on('document_set_id -> documentSetId, 'title -> "document 3").executeInsert()
      val tieNodeToDocumentSql = SQL("INSERT INTO node_document (node_id, document_id) VALUES ({node_id}, {document_id})")
      tieNodeToDocumentSql.on('node_id -> node1Id, 'document_id -> document1Id).execute()
      tieNodeToDocumentSql.on('node_id -> node1Id, 'document_id -> document2Id).execute()
      tieNodeToDocumentSql.on('node_id -> node1Id, 'document_id -> document3Id).execute()
      tieNodeToDocumentSql.on('node_id -> node2Id, 'document_id -> document1Id).execute()
      tieNodeToDocumentSql.on('node_id -> node3Id, 'document_id -> document2Id).execute()
      tieNodeToDocumentSql.on('node_id -> node3Id, 'document_id -> document3Id).execute()
      documentSetId
    }
  }
}
