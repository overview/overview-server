package models

import helpers.TestTag
import org.specs2.mock._
import org.specs2.specification.Scope
import org.overviewproject.test.Specification
import models.core.Node
import org.specs2.specification.Before
import models.core.DocumentIdList

class SubTreeLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  trait MockComponents extends Scope {
    val loader = mock[SubTreeDataLoader]
    val parser = mock[SubTreeDataParser]
    val nodeLoader = mock[NodeLoader]

    val documentSetId = 123l;
    val subTreeLoader = new SubTreeLoader(documentSetId, loader, nodeLoader, parser)

    val dummyDocuments = List(core.Document(10l, "documents", Some("documentCloudId"), Seq(5l), Seq(22l)))
    val dummyDocumentData = List((10l, "actually", "all data"))

    def createTwoDummyNodes(documentIds: List[Long]): List[core.Node] = {
      val (docIds1, docIds2) = documentIds.splitAt(3)
      val documentIdList1 = core.DocumentIdList(docIds1, 19)
      val documentIdList2 = core.DocumentIdList(docIds2, 43)
      List(core.Node(1, "node1", Nil, documentIdList1, Map()),
        core.Node(2, "node2", Nil, documentIdList2, Map()))
    }

  }

  trait TreeContext extends Scope {
    val loader = mock[SubTreeDataLoader]
    val nodeLoader = mock[NodeLoader]

    val documentSetId = 1l
    val documentIdList = DocumentIdList(Seq.empty, 0)
    val root = Node(1, "root", Seq(2, 3), documentIdList, Map())
    val children = Seq(
      Node(2, "child", Seq(4, 5), documentIdList, Map()),
      Node(3, "leaf", Seq.empty, documentIdList, Map()))
    val treeNodes = root +: children.take(2)
    val tagCounts = List()

    val subTreeLoader = new SubTreeLoader(documentSetId, loader, nodeLoader)
  }

  "SubTreeLoader" should {

    "be constructable with default loader and parser" in {
      val subTreeLoader = new SubTreeLoader(1l)

      success
    }

    "load root node" in new MockComponents {
      val root = Node(documentSetId, "root", Seq.empty, null, Map())
      nodeLoader loadRoot (documentSetId) returns Some(root)

      subTreeLoader.loadRoot must beSome.like { case r => r must be equalTo root }
      there was one(nodeLoader).loadRoot(documentSetId)
    }

    "load tree and add tag info" in new TreeContext {
      nodeLoader loadTree (documentSetId, root.id, 1) returns root +: children
      loader loadNodeTagCounts (Seq(1, 2, 3)) returns tagCounts
      
      val nodes = subTreeLoader.load(root.id, 1)
      there was one(nodeLoader).loadTree(documentSetId, root.id, 1)
      there was one(loader).loadNodeTagCounts(Seq(1, 2, 3)) // doesn't actually check arguments
      
      nodes must have size(3)
    }

    // test load()
    "load DocumentIds for unique parent nodes, parsing result" in new MockComponents {
      val nodeData = List((-1l, 1l, "root"), (1l, 2l, "child"), (2l, 4l, "grandChild"),
        (1l, 3l, "child"),
        (3l, 5l, "grandChild"),
        (3l, 6l, "grandchild"),
        (4l, 7l, "extra"),
        (5l, 8l, "extra"),
        (6l, 9l, "extra")).map(d => (d._1, Some(d._2), d._3))
      val nodeIds = List(-1l, 1l, 2l, 3l)
      val extraNodeIds = List(4l, 5l, 6l)
      val documentData = List((1l, 34l, 20l))
      val dummyNodes = List(core.Node(1l, "standin for lots of Nodes", Nil, null, Map()))
      val dummyNodeTagCountsData = List((1l, 2l, 4l))

      loader loadNodeData (documentSetId, 1, 3) returns nodeData
      loader loadDocumentIds (nodeIds ++ extraNodeIds) returns documentData
      loader loadNodeTagCounts (nodeIds) returns dummyNodeTagCountsData

      parser createNodes (nodeData.take(6), documentData, dummyNodeTagCountsData) returns dummyNodes

      val nodes = subTreeLoader.load2(1l, 2)

      there was one(loader).loadNodeData(documentSetId, 1, 3)
      there was one(loader).loadDocumentIds(nodeIds ++ extraNodeIds)
      there was one(loader).loadNodeTagCounts(nodeIds)
      there was one(parser).createNodes(nodeData.take(6), documentData, dummyNodeTagCountsData)

      nodes must be equalTo (dummyNodes)
    }

    "load DocumentIds for leaf nodes with no children" in new MockComponents {
      val nodeData = List((-1l, Some(54l), "in subtree with no children"),
        (54l, None, ""))
      val documentData = List((54l, 34l, 20l), (54l, 34l, 30l))
      val nodeIds = List(-1l, 54l)

      loader loadNodeData (documentSetId, 54l, 3) returns nodeData
      loader loadDocumentIds (nodeIds) returns documentData

      val nodes = subTreeLoader.load2(54l, 2)

      there was one(loader).loadDocumentIds(nodeIds)
    }

    // test loadDocuments() gets passed correct documentIds 
    trait DocumentsInNodesAndTags extends MockComponents {
      val nodeDocumentIds: List[Long]
      val tagDocumentIds: Seq[Long]

      def dummyNodes = createTwoDummyNodes(nodeDocumentIds)
      def dummyTags: Seq[PersistentTagInfo] = Seq(TestTag(1l, "tag1", None, core.DocumentIdList(tagDocumentIds, 12l)))
      def allDocumentIds = nodeDocumentIds ++ tagDocumentIds
    }

    trait DistinctDocumentsInNodesAndTags extends DocumentsInNodesAndTags {
      override val nodeDocumentIds = List(10l, 20l, 30l, 40l, 50l)
      override val tagDocumentIds = Seq(100l, 200l, 300l)
    }

    trait DuplicateDocuments extends DocumentsInNodesAndTags {
      override val nodeDocumentIds = List(10l, 20l, 30l, 10l, 20l)
      override val tagDocumentIds = List(100l, 200l, 20l, 30l)
    }

    trait UnsortedDocuments extends DocumentsInNodesAndTags {
      override val nodeDocumentIds = List(30l, 20l, 40l, 10l, 60l)
      override val tagDocumentIds = List(25l, 15l, 5l)
    }

    "load documents in nodes and tags" in new DistinctDocumentsInNodesAndTags {
      val documents = subTreeLoader.loadDocuments(dummyNodes, dummyTags)

      there was one(loader).loadDocuments(allDocumentIds)
    }

    "only create one document for each unique included document id" in new DuplicateDocuments {
      val documents = subTreeLoader.loadDocuments(dummyNodes, dummyTags)

      there was one(loader).loadDocuments(allDocumentIds.distinct)
    }

    "create documents in sorted order" in new UnsortedDocuments {
      val documents = subTreeLoader.loadDocuments(dummyNodes, dummyTags)

      there was one(loader).loadDocuments(allDocumentIds.sorted)
    }

    // test loadRootId()
    "loads root node from loader" in new MockComponents {
      val dummyRootNodeId = Some(1l)

      loader loadRoot (documentSetId) returns dummyRootNodeId

      val rootId = subTreeLoader.loadRootId()

      there was one(loader).loadRoot(documentSetId)
      rootId must be equalTo (dummyRootNodeId)
    }

    // test loadTags()
    "load tag information for nodes" in new MockComponents {
      val dummyTagData = List((1l, "dummy", 55l, Some(10l), None))
      val dummyTags = List(TestTag(1l, "dummy", None, null))

      loader loadTags (documentSetId) returns dummyTagData
      parser createTags (dummyTagData) returns dummyTags

      val tags = subTreeLoader.loadTags(documentSetId)

      there was one(loader).loadTags(documentSetId)
      there was one(parser).createTags(dummyTagData)

      tags must be equalTo (dummyTags)
    }
  }

}
