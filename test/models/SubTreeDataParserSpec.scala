package models

import org.specs2.mutable.Specification

class SubTreeDataParserSpec extends Specification {
  
  "SubTreeDataParser" should {
    
    "create Nodes from tuples" in {
      
      val nonLeafNodeData = List(
          (-1l, 1l, "root"),
          (1l, 2l, "child1-1"), (1l, 3l, "child1-2")
      )
      val leafNodeData = List(
          (2l, 4l, "child2-1"), (2l, 5l, "child2-2"), (2l, 6l, "child2-3"), (2l, 7l, "child2-4"),
          (3l, 8l, "child3-1"), (3l, 9l, "child3-2"), (3l, 10l, "child3-3")
      )
            
      val nodeData = nonLeafNodeData ++ leafNodeData
      
      val subTreeDataParser = new SubTreeDataParser()
      
      val emptyDocumentData = Nil
      val nodes = subTreeDataParser.createNodes(nodeData, emptyDocumentData)
      
      nodes must have size(nonLeafNodeData.size)
      
      val nodeDescriptions = nonLeafNodeData.map(n => (n._2, n._3))
      
      nodes.map(n => (n.id, n.description)) must haveTheSameElementsAs(nodeDescriptions)
      
      val root = nodes.find(_.id == 1l).get
      val child2 = nodes.find(_.id == 2l).get
      val child3 = nodes.find(_.id == 3l).get
      
      root.childNodeIds   must haveTheSameElementsAs(List(2l, 3l))
      child2.childNodeIds must haveTheSameElementsAs(List(4l, 5l, 6l, 7l))
      child3.childNodeIds must haveTheSameElementsAs(List(8l, 9l, 10l))
    }
    
    "create Nodes with DocumentId Lists" in {
      val nodeData = List(
          (-1l, 1l, "root"), (1l, 2l, "child"), (2l, 3l, "grandchild"))
      val documentData = List(
          (1l, 25l, 100l), (2l, 2l, 101l),
          (1l, 25l, 102l), (2l, 2l, 103l),
          (1l, 25l, 103l),
          (1l, 25l, 104l))
      
      val rootDocuments = documentData.filter(_._1 == 1l).map(_._3)
      val childDocuments = documentData.filter(_._1 == 2l).map(_._3)
      
      val subTreeDataParser = new SubTreeDataParser()
      val nodes = subTreeDataParser.createNodes(nodeData, documentData)
      
      nodes must have size(2)
      
      val root = nodes.find(_.id == 1l).get
      root.documentIds.firstIds must haveTheSameElementsAs(rootDocuments)
      root.documentIds.totalCount must beEqualTo(25l)
      
      val child = nodes.find(_.id == 2l).get
      child.documentIds.firstIds must haveTheSameElementsAs(childDocuments)
      child.documentIds.totalCount must beEqualTo(2l)
    }
    
    "create Documents from tuples in the same order" in {
      val expectedDocs = List(
          core.Document(1l, "title1", "textUrl1", "viewUrl1"),
          core.Document(2l, "title2", "textUrl2", "viewUrl2"),
          core.Document(3l, "title3", "textUrl3", "viewUrl3"))
      
      val documentData = expectedDocs.map(d => (d.id, d.title, d.textUrl, d.viewUrl))

      val subTreeDataParser = new SubTreeDataParser()
      val documents = subTreeDataParser.createDocuments(documentData)
      
      documents must be equalTo expectedDocs
    }
  }

}