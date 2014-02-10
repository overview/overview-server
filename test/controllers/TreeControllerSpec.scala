package controllers

import org.specs2.specification.Scope

import org.overviewproject.tree.orm.{DocumentSet, Tree}

class TreeControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[TreeController.Storage]

    val controller = new TreeController {
      override val storage = mockStorage
    }
    def show(documentSetId: Long, treeId: Long) = controller.show(documentSetId, treeId)(fakeAuthorizedRequest)
  }

  trait ValidShowScope extends BaseScope {
    val mockTree = mock[Tree]
    mockTree.documentSetId returns 1

    mockStorage.findDocumentSet(1) returns Some(mock[DocumentSet])
    mockStorage.findTree(2) returns Some(mockTree)

    lazy val result = show(1, 2)
  }

  "TreeController.show" should {
    "return NotFound when the DocumentSet is not present" in new BaseScope {
      mockStorage.findDocumentSet(1) returns None
      mockStorage.findTree(2) returns Some(mock[Tree])

      h.status(show(1, 2)) must beEqualTo(h.NOT_FOUND)
    }

    "return NotFound when the DocumentSet is present but the Tree is not" in new BaseScope {
      mockStorage.findDocumentSet(1) returns Some(mock[DocumentSet])
      mockStorage.findTree(2) returns None

      h.status(show(1, 2)) must beEqualTo(h.NOT_FOUND)
    }

    "return NotFound when the Tree does not belong to the DocumentSet" in new BaseScope {
      val mockTree = mock[Tree]
      mockTree.documentSetId returns 2L

      mockStorage.findDocumentSet(1) returns Some(mock[DocumentSet])
      mockStorage.findTree(2) returns None

      h.status(show(1, 2)) must beEqualTo(h.NOT_FOUND)
    }

    "return Ok when okay" in new ValidShowScope {
      h.status(result) must beEqualTo(h.OK)
    }

    "return some HTML when okay" in new ValidShowScope {
      h.contentType(result) must beSome("text/html")
    }

    "return data-is-searchable=false when it is not" in new ValidShowScope {
      mockStorage.isDocumentSetSearchable(any[DocumentSet]) returns false
      h.contentAsString(result) must contain("""<div id="main" data-is-searchable="false"></div>""")
    }

    "return data-is-searchable=true when it is" in new ValidShowScope {
      mockStorage.isDocumentSetSearchable(any[DocumentSet]) returns true
      h.contentAsString(result) must contain("""<div id="main" data-is-searchable="true"></div>""")
    }
  }
}
