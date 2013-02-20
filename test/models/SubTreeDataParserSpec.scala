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
  
    "create Documents from tuples in the same order" in {
      val expectedDocs = Seq(
        core.Document(1l, "description1", Some("title1"), Some("documentCloudId1"), Seq(5l, 15l), Seq(22l, 11l)),
        core.Document(2l, "description2", Some("title2"), Some("documentCloudId2"), Seq(15l), Seq(22l)),
        core.Document(3l, "description3", Some("title3"), Some("documentCloudId3"), Nil, Seq(22l)))

      val documentData = expectedDocs.map(d => (d.id, d.description, d.documentCloudId, d.title))
      val tagData = Seq((1l, 5l), (1l, 15l), (2l, 15l))
      val nodeData = Seq((1l, 22l), (1l, 11l), (2l, 22l), (3l, 22l))

      val subTreeDataParser = new SubTreeDataParser()
      val documents = subTreeDataParser.createDocuments(documentData, tagData, nodeData)

      documents must be equalTo expectedDocs
    }
  }
}
