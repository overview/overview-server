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

    val sampleTree = Tree(
      id = 1L,
      documentSetId = 1L,
      title = "Tree title",
      documentCount = 10,
      lang = "en",
      suppliedStopWords = "",
      importantWords = ""
    )

    val sampleNode = Node(
      id=1L,
      treeId = 1L,
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

  "index" should {
    "encode the trees, nodes, tags, and search results into the json result" in new TestScope {
      mockStorage.findVizs(sampleTree.documentSetId) returns Seq(sampleTree)
      mockStorage.findVizJobs(sampleTree.documentSetId) returns Seq()
      mockStorage.findTree(1L) returns Some(sampleTree)
      mockStorage.findRootNodes(1L, 2) returns Seq(sampleNode)
      mockStorage.findSearchResults(1L) returns Seq(sampleSearchResult)
      mockStorage.findTags(1L) returns Seq(sampleTag)

      val result = index(1L)
      h.status(result) must beEqualTo(h.OK)

      val resultJson = h.contentAsString(result)
      resultJson must /("vizs") */("type" -> "viz")
      resultJson must /("vizs") */("title" -> sampleTree.title)
      resultJson must /("nodes") */("description" -> "description")
      resultJson must /("tags") */("name" -> "a tag")
      resultJson must /("searchResults") */("query" -> "a search query")
    }

    "include viz creation jobs" in new TestScope {
      mockStorage.findVizs(sampleTree.documentSetId) returns Seq()
      mockStorage.findVizJobs(sampleTree.documentSetId) returns Seq(DocumentSetCreationJob(
        id=2L,
        documentSetId=sampleTree.documentSetId,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.InProgress
      ), DocumentSetCreationJob(
        id=3L,
        documentSetId=sampleTree.documentSetId,
        treeTitle=Some("tree job"),
        jobType=DocumentSetCreationJobType.Recluster,
        state=DocumentSetCreationJobState.Error
      ))
      mockStorage.findTree(1L) returns Some(sampleTree)
      mockStorage.findRootNodes(1L, 2) returns Seq(sampleNode)
      mockStorage.findSearchResults(1L) returns Seq(sampleSearchResult)
      mockStorage.findTags(1L) returns Seq(sampleTag)

      val result = index(1L)
      val json = h.contentAsString(result)

      json must /("vizs") /#(0) /("type" -> "job")
      json must /("vizs") /#(0) /("id" -> 2.0)
      json must /("vizs") /#(0) /("creationData") /#(0) /("lang")
      json must /("vizs") /#(0) /("creationData") /#(0) /("en")
      json must /("vizs") /#(1) /("type" -> "error")
    }

    "returns a 404 when no nodes were found" in new TestScope {
      mockStorage.findTree(1L) returns Some(sampleTree)
      mockStorage.findRootNodes(1L, 2) returns Seq()

      val result = index(1L)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }
  }
}
