package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers

import controllers.auth.AuthorizedRequest
import org.overviewproject.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState, Node, Tag, SearchResult, SearchResultState, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import models.OverviewUser
import models.orm.User

class NodeControllerSpec extends ControllerSpecification with JsonMatchers {
  trait TestScope extends Scope {
    val mockStorage = mock[NodeController.Storage]
    val controller = new NodeController {
      override val storage = mockStorage
    }
    def postRequest = fakeAuthorizedRequest.withFormUrlEncodedBody("description" -> "new description")

    def index(treeId: Long) = controller.index(treeId)(fakeAuthorizedRequest)
    def show(treeId: Long, nodeId: Long) = controller.show(treeId, nodeId)(fakeAuthorizedRequest)
    def update(treeId: Long, nodeId: Long) = controller.update(treeId, nodeId)(postRequest)

    val sampleNode = Node(
      id=1L,
      treeId = 1L,
      parentId=None,
      description="description",
      cachedSize=0,
      isLeaf=false
    )
  }

  "update" should {
    "edit a node" in new TestScope {
      mockStorage.findNode(1L, 1L) returns Seq(sampleNode)
      mockStorage.updateNode(any[Node]) returns sampleNode // unused

      val result = update(1L, 1L)
      there was one(mockStorage).updateNode(sampleNode.copy(description="new description"))
    }

    "return NotFound when a node isn't found" in new TestScope {
      mockStorage.findNode(1L, 1L) returns Seq()
      val result = update(1L, 1L)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }
  }

  "show" should {
    "fetches a node and renders it as json" in new TestScope {
      mockStorage.findChildNodes(1L, 1L) returns Seq(sampleNode.copy(description="some stuff"))

      val result = show(1L, 1L)
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beMatching(""".*"some stuff".*""")
      h.header(h.CACHE_CONTROL, result) must beSome("max-age=0")
    }

    "renders an empty list when no nodes are found" in new TestScope {
      mockStorage.findChildNodes(1L, 1L) returns Seq()

      val result = show(1L, 1L)
      h.status(result) must beEqualTo(h.OK)
      h.contentAsString(result) must beEqualTo("""{"nodes":[]}""")
      h.header(h.CACHE_CONTROL, result) must beSome("max-age=0")
    }
  }
}
