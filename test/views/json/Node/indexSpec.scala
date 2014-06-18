package views.json.Node

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.tree.orm.Node

class indexSpec extends Specification with JsonMatchers {
  private def buildNode(id: Long, parentId: Option[Long], cachedSize: Int, cachedDocumentIds: Array[Long]) : Node = {
    Node(
      id=id,
      treeId = 1L,
      parentId=parentId,
      description="description",
      cachedSize=cachedSize,
      cachedDocumentIds=cachedDocumentIds,
      isLeaf=false
    )
  }

  "Tree view generated Json" should {
    "contain all nodes" in {
      val nodes = List(
        buildNode(1, None, 2, Array(1L, 2L)),
        buildNode(2, Some(1L), 1, Array(1L)),
        buildNode(3, Some(1L), 1, Array(2L))
      )

      val treeJson = index(nodes).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }
  }
}
