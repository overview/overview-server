package models

import org.specs2.mutable.Specification

class SubTreeDataParserSpec extends Specification {
  
  "SubTreeDataParser" should {
    
    "create Nodes from tuples" in {
      
      val nodeData = List(
          (-1l, 1l, "root"),
          (1l, 2l, "child1-1"), (1l, 3l, "child1-2"),
          (2l, 4l, "child2-1"), (2l, 5l, "child2-2"), (2l, 6l, "child2-3"), (2l, 7l, "child2-4"),
          (3l, 8l, "child3-1"), (3l, 9l, "child3-2"), (3l, 10l, "child3-3")
      )
      
      val subTreeDataParser = new SubTreeDataParser()
      
      val emptyDocumentData = Nil
      val nodes = subTreeDataParser.createNodes(nodeData, emptyDocumentData)
      
      nodes must have size(nodeData.size)
      
      val nodeDescriptions = nodeData.map(n => (n._2, n._3))
      
      nodes.map(n => (n.id, n.description)) must haveTheSameElementsAs(nodeDescriptions)
      
      val root = nodes.find(_.id == 1l).get
      val child2 = nodes.find(_.id == 2l).get
      val child3 = nodes.find(_.id == 3l).get
      
      root.childNodeIds   must haveTheSameElementsAs(List(2l, 3l))
      child2.childNodeIds must haveTheSameElementsAs(List(4l, 5l, 6l, 7l))
      child3.childNodeIds must haveTheSameElementsAs(List(8l, 9l, 10l))
      
      val leafIds = 4l to 10l
      val leafNodes = leafIds.flatMap(i => nodes.find(_.id == i))
      
      leafNodes.flatMap(_.childNodeIds) must be empty
       
    }
    
    "create Nodes with DocumentId Lists" in {
      val nodeData = List(
          (-1l, 1l, "root"), (1l, 2l, "child"), (2l, 3l, "grandchild"))
      val documentData = List(
          (1l, 25l, 100l), (2l, 2l, 101l),
          (1l, 25l, 102l), (2l, 2l, 103l),
          (1l, 25l, 103l),
          (1l, 25l, 104l), (3l, 1l, 105l))
      
      val rootDocuments = documentData.filter(_._1 == 1l).map(_._3)
      val childDocuments = documentData.filter(_._1 == 2l).map(_._3)
      val grandChildDocuments = documentData.filter(_._1 == 3l).map(_._3)
      
      val subTreeDataParser = new SubTreeDataParser()
      val nodes = subTreeDataParser.createNodes(nodeData, documentData)
      
      nodes must have size(nodeData.size)
      
      val root = nodes.find(_.id == 1l).get
      root.documentIds.firstIds must haveTheSameElementsAs(rootDocuments)
      root.documentIds.totalCount must beEqualTo(25l)
      
      val child = nodes.find(_.id == 2l).get
      child.documentIds.firstIds must haveTheSameElementsAs(childDocuments)
      child.documentIds.totalCount must beEqualTo(2l)
      
      val grandChild = nodes.find(_.id == 3l).get
      grandChild.documentIds.firstIds must haveTheSameElementsAs(grandChildDocuments)
      grandChild.documentIds.totalCount must beEqualTo(1l)
     
    }
    
    "create Documents from tuples" in {
      val documentData = List(
          (1l, "title1", "textUrl1", "viewUrl1"),
          (2l, "title2", "textUrl2", "viewUrl2"),
          (3l, "title3", "textUrl3", "viewUrl3")
      )
      
      val subTreeDataParser = new SubTreeDataParser()
      val documents = subTreeDataParser.createDocuments(documentData)
      
      documents must have size(3)
      
      documents(0) must be equalTo core.Document(1l, "title1", "textUrl1", "viewUrl1")
      documents(1) must be equalTo core.Document(2l, "title2", "textUrl2", "viewUrl2")
      documents(2) must be equalTo core.Document(3l, "title3", "textUrl3", "viewUrl3")
    }
  }

}