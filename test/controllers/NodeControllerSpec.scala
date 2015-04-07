package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers

import controllers.auth.AuthorizedRequest
import org.overviewproject.tree.orm.{DocumentSetCreationJob, DocumentSetCreationJobState, Node, Tag, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import models.OverviewUser
import models.User

class NodeControllerSpec extends ControllerSpecification with JsonMatchers {
  trait TestScope extends Scope {
    val mockStorage = mock[NodeController.Storage]
    val controller = new NodeController {
      override val storage = mockStorage
    }
    def postRequest = fakeAuthorizedRequest.withFormUrlEncodedBody("description" -> "new description")

    def index(treeId: Long) = controller.index(treeId)(fakeAuthorizedRequest)
    def show(treeId: Long, nodeId: Long) = controller.show(treeId, nodeId)(fakeAuthorizedRequest)

    val sampleNode = Node(
      id=1L,
      rootId=1L,
      parentId=None,
      description="description",
      cachedSize=0,
      isLeaf=false
    )
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
