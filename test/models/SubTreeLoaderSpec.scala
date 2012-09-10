package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SubTreeLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  trait MockComponents extends Scope {
    val loader = mock[SubTreeDataLoader]
    val parser = mock[SubTreeDataParser]
    val documentSetId = 123l;
    val subTreeLoader = new SubTreeLoader(documentSetId, loader, parser)

    val dummyDocuments = List(core.Document(10l, "documents", "created", "from data", Seq(5l)))
    val dummyDocumentData = List((10l, "actually", "all", "documentdata"))

    def createTwoDummyNodes(documentIds: List[Long]): List[core.Node] = {
      val (docIds1, docIds2) = documentIds.splitAt(3)
      val documentIdList1 = core.DocumentIdList(docIds1, 19)
      val documentIdList2 = core.DocumentIdList(docIds2, 43)
      List(core.Node(1, "node1", Nil, documentIdList1, Map()),
        core.Node(2, "node2", Nil, documentIdList2, Map()))
    }

  }

  "SubTreeLoader" should {

    "load DocumentIds for unique parent nodes, parsing result" in new MockComponents {
      val nodeData = List((-1l, 1l, "root"), (1l, 2l, "child"), (1l, 3l, "child"),
        (2l, 4l, "grandChild"), (3l, 5l, "grandChild"),
        (3l, 5l, "grandchild")).map(d => (d._1, Some(d._2), d._3))
      val nodeIds = List(-1l, 1l, 2l, 3l)
      val documentData = List((1l, 34l, 20l))
      val dummyNodes = List(core.Node(1l, "standin for lots of Nodes", Nil, null, Map()))
      val dummyNodeTagCountsData = List((1l, 2l, 4l))

      loader loadNodeData (documentSetId, 1, 2) returns nodeData
      loader loadDocumentIds (nodeIds) returns documentData
      loader loadNodeTagCounts (nodeIds) returns dummyNodeTagCountsData

      parser createNodes (nodeData, documentData, dummyNodeTagCountsData) returns dummyNodes

      val nodes = subTreeLoader.load(1l, 2)

      there was one(loader).loadNodeData(documentSetId, 1, 2)
      there was one(loader).loadDocumentIds(nodeIds)
      there was one(loader).loadNodeTagCounts(nodeIds)
      there was one(parser).createNodes(nodeData, documentData, dummyNodeTagCountsData)

      nodes must be equalTo (dummyNodes)
    }

    "load DocumentIds for leaf nodes with no children" in new MockComponents {
      val nodeData = List((-1l, Some(54l), "in subtree with no children"),
        (54l, None, ""))
      val documentData = List((54l, 34l, 20l), (54l, 34l, 30l))
      val nodeIds = List(-1l, 54l)

      loader loadNodeData (documentSetId, 1, 2) returns nodeData
      loader loadDocumentIds (nodeIds) returns documentData

      val nodes = subTreeLoader.load(1, 2)

      there was one(loader).loadDocumentIds(nodeIds)
    }

    "call loader and parser to create documents from nodes and tags" in new MockComponents {
      val documentIds = List(10l, 20l, 30l, 40l, 50l)
      val dummyNodeList = createTwoDummyNodes(documentIds)
      val dummyTagData = List((10l, 5l), (20l, 15l))

      val tagDocumentIds = Seq(100l, 200l, 300l)
      val dummyTag = core.Tag(2, "tag1", core.DocumentIdList(tagDocumentIds, 12l))

      val allDocumentIds = documentIds ++ tagDocumentIds

      loader loadDocuments (allDocumentIds) returns dummyDocumentData
      loader loadDocumentTags (allDocumentIds) returns dummyTagData

      parser createDocuments (dummyDocumentData, dummyTagData) returns dummyDocuments

      val documents = subTreeLoader.loadDocuments(dummyNodeList, Seq(dummyTag))

      there was one(loader).loadDocuments(allDocumentIds)
      there was one(loader).loadDocumentTags(allDocumentIds)
      there was one(parser).createDocuments(dummyDocumentData, dummyTagData)
    }
    
    "not duplicate documents included in multiple nodes" in new MockComponents {
      val documentIds = List(10l, 20l, 30l, 10l, 20l, 30l)
      val dummyNodeList = createTwoDummyNodes(documentIds)
      val dummyTagData = Nil

      loader loadDocuments (documentIds.distinct) returns dummyDocumentData
      parser createDocuments (dummyDocumentData, dummyTagData) returns dummyDocuments

      val documents = subTreeLoader.loadDocuments(dummyNodeList, Nil)

      there was one(loader).loadDocuments(documentIds.distinct)
    }

    "create documents in sorted order" in new MockComponents {
      val documentIds = List(30l, 20l, 40l, 10l, 60l, 50l)
      val dummyNodeList = createTwoDummyNodes(documentIds)
      val dummyTagData = Nil

      loader loadDocuments (documentIds.sorted) returns dummyDocumentData
      parser createDocuments (dummyDocumentData, dummyTagData) returns dummyDocuments

      val documents = subTreeLoader.loadDocuments(dummyNodeList, Nil)

      there was one(loader).loadDocuments(documentIds.sorted)
    }

    "loads root node from loader" in new MockComponents {
      val dummyRootNodeId = Some(1l)

      loader loadRoot (documentSetId) returns dummyRootNodeId

      val rootId = subTreeLoader.loadRootId()

      there was one(loader).loadRoot(documentSetId)
      rootId must be equalTo (dummyRootNodeId)
    }

    "load tag information for nodes" in new MockComponents {
      val dummyTagData = List((1l, "dummy", 55l, Some(10l)))
      val dummyTags = List(core.Tag(1l, "dummy", null))

      loader loadTags (documentSetId) returns dummyTagData
      parser createTags (dummyTagData) returns dummyTags

      val tags = subTreeLoader.loadTags(documentSetId)

      there was one(loader).loadTags(documentSetId)
      there was one(parser).createTags(dummyTagData)

      tags must be equalTo (dummyTags)
    }

    "be constructable with default loader and parser" in {
      val subTreeLoader = new SubTreeLoader(1l)

      success
    }

  }

}
