package models

import org.overviewproject.test.Specification

class SubTreeDataParserSpec extends Specification {

  "SubTreeDataParser" should {

    "create Nodes from orm.Nodes and tagData" in {

      val documentSetId = 11
      val nodes = Seq.tabulate(10)(core.Node(_, "node", Seq.empty, null, Map()))
      val nodeTagCountData = nodes.map { n => (n.id, n.id * 10, 5l) } // (nodeId, tagId, tagCount)

      val subTreeDataParser = new SubTreeDataParser

      val createdNodes = subTreeDataParser.addTagDataToNodes(nodes, nodeTagCountData)

      createdNodes.size must be equalTo (nodes.size)
      createdNodes.map { n =>
        val tagId = (n.id * 10).toString
        n.tagCounts.get(tagId) must beSome(5l)
      }

    }
  }
}
