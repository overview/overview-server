package views.json.Tree

import play.api.libs.json.Json.toJson
import org.specs2.mutable.Specification

import org.overviewproject.tree.orm.{Node,Document,DocumentType}
import models.orm.Tag

class showSpec extends Specification {
  def buildNode(id: Long, parentId: Option[Long], cachedSize: Int, cachedDocumentIds: Array[Long]) : Node = {
    Node(
      id=id,
      documentSetId=1L,
      parentId=parentId,
      description="description",
      cachedSize=cachedSize,
      cachedDocumentIds=cachedDocumentIds
    )
  }
  
  "Tree view generated Json" should {
    
    "contain all nodes" in {
      val nodes = List(
        (buildNode(1, None, 2, Array(1L, 2L)), Seq(1L, 2L), Seq[(Long,Long)](), Seq[(Long,Long)]()),
        (buildNode(2, Some(1L), 1, Array(1L)), Seq[Long](), Seq[(Long,Long)](), Seq[(Long,Long)]()),
        (buildNode(3, Some(1L), 1, Array(2L)), Seq[Long](), Seq[(Long,Long)](), Seq[(Long,Long)]())
      )

      val treeJson = show(nodes, Seq(), Seq(), Seq()).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }

    "contain all documents" in {
      val nodes = Seq[(Node,Iterable[Long],Iterable[(Long,Long)], Iterable[(Long, Long)])]()
      val documentsWithNodeIdsAndTagIds = Seq(
        (Document(id=10l, documentType=DocumentType.CsvImportDocument, description="description", title=Some("title")), Seq(5L), Seq(1L)),
        (Document(id=20l, documentType=DocumentType.CsvImportDocument, description="description", title=Some("title")), Seq(5L), Seq(1L)),
        (Document(id=30l, documentType=DocumentType.CsvImportDocument, description="description", title=Some("title")), Seq(5L), Seq(1L))
      )

      val treeJson = show(nodes, documentsWithNodeIdsAndTagIds, Seq(), Seq()).toString
      
      treeJson must /("documents") */("id" -> 10l)
      treeJson must /("documents") */("id" -> 20l)
      treeJson must /("documents") */("id" -> 30l)
    }
    
    "contain tags" in {
      val nodes = Seq[(Node,Iterable[Long],Iterable[(Long,Long)], Iterable[(Long, Long)])]()
      val dummyDocuments = Seq[(Document,Seq[Long],Seq[Long])]()

      val baseTag = Tag(id=5L, name="tag1", documentSetId=1L, color=Some("ffffff"))

      val tags = List[(Tag,Long)](
        (baseTag.copy(id=5L, name="tag1"), 5L),
        (baseTag.copy(id=15L, name="tag2"), 10L)
      )
      val treeJson = show(nodes, dummyDocuments, tags, Seq()).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }
  }
}
