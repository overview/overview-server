package controllers

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.test.{FakeApplication, FakeRequest}
import play.api.test.Helpers._
import play.api.Play.{start,stop}

import controllers.auth.AuthorizedRequest
import org.overviewproject.tree.orm.{Node, Tag, SearchResult, SearchResultState}
import models.OverviewUser
import models.orm.User

class NodeControllerSpec extends Specification with Mockito {
  step(start(FakeApplication()))

  trait TestScope extends Scope {
    val mockStorage = mock[NodeController.Storage]
    val controller = new NodeController {
      override val storage = mockStorage
    }
    val user = mock[OverviewUser]
    def getRequest = new AuthorizedRequest(FakeRequest(), user)
    def postRequest = new AuthorizedRequest(FakeRequest().withFormUrlEncodedBody("description" -> "new description"), user)

    def index(documentSetId: Long) = controller.index(documentSetId)(getRequest)
    def show(documentSetId: Long, nodeId: Long) = controller.show(documentSetId, nodeId)(getRequest)
    def update(documentSetId: Long, nodeId: Long) = controller.update(documentSetId, nodeId)(postRequest)

    val sampleNode = Node(
      id=1L,
      treeId = 1L,
      documentSetId=1L,
      parentId=None,
      description="description",
      cachedSize=0,
      cachedDocumentIds=Array[Long](),
      isLeaf=false
    )

    val sampleTag = Tag(
      id=1L,
      documentSetId=1L,
      name="a tag",
      color="FFFFFF"
    )

    val sampleSearchResult = SearchResult(
      id=1L,
      documentSetId=1L,
      query="a search query",
      state=SearchResultState.Complete
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
      status(result) must beEqualTo(NOT_FOUND)
    }
  }

  "show" should {
    "fetches a node and renders it as json" in new TestScope {
      mockStorage.findChildNodes(1L, 1L) returns Seq(sampleNode.copy(description="some stuff"))

      val result = show(1L, 1L)
      status(result) must beEqualTo(OK)
      contentAsString(result) must beMatching(""".*"some stuff".*""")
      header(CACHE_CONTROL, result) must beSome("max-age=0")
    }

    "renders an empty list when no nodes are found" in new TestScope {
      mockStorage.findChildNodes(1L, 1L) returns Seq()

      val result = show(1L, 1L)
      status(result) must beEqualTo(OK)
      contentAsString(result) must beEqualTo("""{"nodes":[]}""")
      header(CACHE_CONTROL, result) must beSome("max-age=0")
    }
  }

  "index" should {
    "encodes the nodes, tags, and search results into the json result" in new TestScope {
      mockStorage.findRootNodes(1L, 2) returns Seq(sampleNode)
      mockStorage.findSearchResults(1L) returns Seq(sampleSearchResult)
      mockStorage.findTags(1L) returns Seq(sampleTag)

      val result = index(1L)
      status(result) must beEqualTo(OK)

      val resultJson = contentAsString(result)
      resultJson must /("nodes") */("description" -> "description")
      resultJson must /("tags") */("name" -> "a tag")
      resultJson must /("searchResults") */("query" -> "a search query")
    }

    "returns a 404 when no nodes were found" in new TestScope {
      mockStorage.findRootNodes(1L, 2) returns Seq()

      val result = index(1L)
      status(result) must beEqualTo(NOT_FOUND)
    }
  }

  step(stop)
}
