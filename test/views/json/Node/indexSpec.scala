package views.json.Node

import java.util.Date
import org.specs2.matcher.JsonMatchers
import org.specs2.mutable.Specification

import org.overviewproject.tree.orm.Node

class indexSpec extends Specification with JsonMatchers {
  private def buildNode(id: Long, parentId: Option[Long], cachedSize: Int) : Node = {
    Node(
      id=id,
      rootId = 1L,
      parentId=parentId,
      description="description",
      cachedSize=cachedSize,
      isLeaf=parentId.isDefined
    )
  }

  "Tree view generated Json" should {
    "contain all nodes" in {
      val nodes = List(
        buildNode(1, None, 2),
        buildNode(2, Some(1L), 1),
        buildNode(3, Some(1L), 1)
      )

      val treeJson = index(nodes).toString
      
      treeJson must /("nodes") */("id" -> 1)
      treeJson must /("nodes") */("id" -> 2)
      treeJson must /("nodes") */("id" -> 3)
    }
  }
}
