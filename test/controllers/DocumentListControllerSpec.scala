package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.Play.{start, stop}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._

import controllers.auth.AuthorizedRequest
import models.{OverviewUser,Selection,ResultPage}

class DocumentListControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

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

    val user = mock[OverviewUser]
    val request = new AuthorizedRequest(FakeRequest(), user)
    lazy val result = controller.index(documentSetId, nodes, tags, documents, searchResults, untagged, pageSize, page)(request)
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

  step(stop)
}
