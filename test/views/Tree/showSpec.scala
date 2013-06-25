package views.json.Tree

import play.api.libs.json.Json.toJson
import org.specs2.mutable.Specification

import org.overviewproject.tree.orm.{Node,Document,DocumentType,Tag}

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
        (buildNode(1, None, 2, Array(1L, 2L)), Seq(1L, 2L)),
        (buildNode(2, Some(1L), 1, Array(1L)), Seq[Long]()),
        (buildNode(3, Some(1L), 1, Array(2L)), Seq[Long]())
      )

      val treeJson = show(nodes, Seq(), Seq()).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }

    "contain tags" in {
      val nodes = Seq[(Node,Iterable[Long])]()

      val baseTag = Tag(id=5L, name="tag1", documentSetId=1L, color="ffffff")

      val tags = Seq[Tag](
        baseTag.copy(id=5L, name="tag1"),
        baseTag.copy(id=15L, name="tag2")
      )
      val treeJson = show(nodes, tags, Seq()).toString
      
      treeJson must /("tags") */("id" -> 5L)
      treeJson must /("tags") */("name" -> "tag1")
      treeJson must /("tags") */("id" -> 15L)
    }
  }
}
