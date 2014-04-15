package controllers

import org.specs2.specification.Scope
import play.api.mvc.AnyContent

import controllers.auth.AuthorizedRequest
import models.Selection
import org.overviewproject.tree.orm.finders.ResultPage

class DocumentListControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[DocumentListController.Storage]

    val controller = new DocumentListController {
      override val storage = mockStorage
    }

    val documentSetId = 1L
    val nodes = ""
    val tags = ""
    val documents = ""
    val searchResults = ""
    val pageSize = 10
    val page = 1
    val untagged = false
    
    def expectedSelection = Selection(
      documentSetId=documentSetId,
      nodeIds=Seq[Long](),
      tagIds=Seq[Long](),
      documentIds=Seq[Long](),
      searchResultIds=Seq[Long](),
      untagged=false
    )

    val request : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest
    lazy val result = controller.index(documentSetId, nodes, tags, documents, searchResults, pageSize, page)(request)
  }

  trait StubbedScope extends BaseScope {
    mockStorage.findDocuments(expectedSelection, pageSize, page) returns ResultPage(Seq(), pageSize, page)
  }

  "DocumentListController" should {
    "index() should find documents based on passed selection" in new StubbedScope {
      override val nodes = "1,2,3"
      override def expectedSelection = super.expectedSelection.copy(nodeIds=Seq(1L, 2L, 3L))

      result
      there was one(mockStorage).findDocuments(expectedSelection, pageSize, page)
    }
  }
}
