package controllers

import org.specs2.specification.Scope

import com.overviewdocs.test.factories.{PodoFactory=>factory}

class NodeControllerSpec extends ControllerSpecification {
  trait TestScope extends Scope {
    val mockStorage = mock[NodeController.Storage]
    val controller = new NodeController with TestController {
      override val storage = mockStorage
    }
    def postRequest = fakeAuthorizedRequest.withFormUrlEncodedBody("description" -> "new description")

    def index(treeId: Long) = controller.index(treeId)(fakeAuthorizedRequest)
    def show(treeId: Long, nodeId: Long) = controller.show(treeId, nodeId)(fakeAuthorizedRequest)
  }

  "show" should {
    "fetches a node and renders it as json" in new TestScope {
      mockStorage.findChildNodes(1L, 1L) returns Seq(factory.node(description="some stuff"))

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
