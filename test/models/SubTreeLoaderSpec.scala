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

    val dummyDocuments = List(core.Document(10l, "documents", Some("title"), Some("documentCloudId"), Seq(5l), Seq(22l)))
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
    
    "load tree and add tag info" in new TreeContext {
      nodeLoader loadTree (documentSetId, root.id, 1) returns root +: children
      loader loadNodeTagCounts (Seq(1, 2, 3)) returns tagCounts
      
      val nodes = subTreeLoader.load(root.id, 1)
      there was one(nodeLoader).loadTree(documentSetId, root.id, 1)
      there was one(loader).loadNodeTagCounts(Seq(1, 2, 3)) // doesn't actually check arguments
      
      nodes must have size(3)
    }

    // test loadDocuments() gets passed correct documentIds 
    trait DocumentsInNodesAndTags extends MockComponents {
      val nodeDocumentIds: List[Long]

      def dummyNodes = createTwoDummyNodes(nodeDocumentIds)
      def allDocumentIds = nodeDocumentIds
    }

    trait DistinctDocumentsInNodesAndTags extends DocumentsInNodesAndTags {
      override val nodeDocumentIds = List(10l, 20l, 30l, 40l, 50l)
    }

    trait DuplicateDocuments extends DocumentsInNodesAndTags {
      override val nodeDocumentIds = List(10l, 20l, 30l, 10l, 20l)
    }

    trait UnsortedDocuments extends DocumentsInNodesAndTags {
      override val nodeDocumentIds = List(30l, 20l, 40l, 10l, 60l)
    }

    "load documents in nodes and tags" in new DistinctDocumentsInNodesAndTags {
      val documents = subTreeLoader.loadDocuments(dummyNodes)

      there was one(loader).loadDocuments(allDocumentIds)
    }

    "only create one document for each unique included document id" in new DuplicateDocuments {
      val documents = subTreeLoader.loadDocuments(dummyNodes)

      there was one(loader).loadDocuments(allDocumentIds.distinct)
    }

    "create documents in sorted order" in new UnsortedDocuments {
      val documents = subTreeLoader.loadDocuments(dummyNodes)

      there was one(loader).loadDocuments(allDocumentIds.sorted)
    }

    // test loadRootId()
    "loads root node from loader" in new TreeContext {
      val dummyRootNodeId = Some(1l)

      nodeLoader loadRootId (documentSetId) returns dummyRootNodeId

      val rootId = subTreeLoader.loadRootId()

      there was one(nodeLoader).loadRootId(documentSetId)
      rootId must be equalTo (dummyRootNodeId)
    }
  }

}
