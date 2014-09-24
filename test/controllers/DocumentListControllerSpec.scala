package controllers

import org.specs2.specification.Scope
import play.api.mvc.AnyContent

import controllers.auth.AuthorizedRequest
import models.SelectionRequest
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
    val tagged: Option[Boolean] = None
    
    def expectedSelection = SelectionRequest(
      documentSetId=documentSetId,
      nodeIds=Seq[Long](),
      tagIds=Seq[Long](),
      documentIds=Seq[Long](),
      searchResultIds=Seq[Long](),
      tagged=None
    )

    lazy val request : AuthorizedRequest[AnyContent] = fakeAuthorizedRequest("GET", s"?nodes=$nodes&tags=$tags&documents=$documents&searchResults=$searchResults")
    lazy val result = controller.index(documentSetId, pageSize, page)(request)
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
