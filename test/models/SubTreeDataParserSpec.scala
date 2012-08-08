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
      val nodeTagCountData = Nil
      
      
      val nodeData = (nonLeafNodeData ++ leafNodeData).map(d => (d._1, Some(d._2), d._3))
      
      val subTreeDataParser = new SubTreeDataParser()
      
      val documentData = List((1l, 1l, 10l), (2l, 1l, 10l), (3l, 1l, 10l))
      val nodes = subTreeDataParser.createNodes(nodeData, documentData, nodeTagCountData)
      
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
    
    "create Nodes with empty childNodeId List for leafnodes" in {
      val leafNodeData = List((-1l, Some(1l), "node"), (1l, None, ""))
      val documentData = List((1l, 2l, 10l), (1l, 2l, 20l))
      val nodeTagCountData = Nil
      
      val subTreeDataParser = new SubTreeDataParser()
      
      val nodes = subTreeDataParser.createNodes(leafNodeData, documentData, nodeTagCountData)
      
      nodes must have size(1)
      nodes(0).childNodeIds must be empty

    }

    "create Nodes with tag counts" in {
      val nodeData = List((-1l, Some(1l), "root"), 
                          (1l, Some(2l), "child1-1"), (1l, Some(3l), "child1-2"),
                          (2l, None, ""), (3l, None, ""))
                          
      val documentData = List((1l, 2l, 10l), (1l, 2l, 20l), (2l, 1l, 10l), (3l, 1l, 20l))
      val nodeTagCountData = List((1l, 5l, 22l), (1l, 15l, 12l), (2l, 5l, 3l))      
      
      val subTreeDataParser = new SubTreeDataParser()
      
      val nodes = subTreeDataParser.createNodes(nodeData, documentData, nodeTagCountData)
            
      val root = nodes.find(_.id == 1l).get
      val child2 = nodes.find(_.id == 2l).get
      val child3 = nodes.find(_.id == 3l).get
      
      root.tagCounts must haveTheSameElementsAs(Map(("5" -> 22l), ("15", 12l)))
      child2.tagCounts must haveTheSameElementsAs(Map(("5" -> 3l)))
      child3.tagCounts must be empty
    }
    
    
    "create Nodes with DocumentId Lists" in {
      val nodeData = List(
          (-1l, 1l, "root"), (1l, 2l, "child"), (2l, 3l, "grandchild")).
          map(d => (d._1, Some(d._2), d._3))
          
      val documentData = List(
          (1l, 25l, 100l), (2l, 2l, 101l),
          (1l, 25l, 102l), (2l, 2l, 103l),
          (1l, 25l, 103l),
          (1l, 25l, 104l))
      
      val nodeTagCountData = Nil
      
      val rootDocuments = documentData.filter(_._1 == 1l).map(_._3)
      val childDocuments = documentData.filter(_._1 == 2l).map(_._3)
      
      val subTreeDataParser = new SubTreeDataParser()
      val nodes = subTreeDataParser.createNodes(nodeData, documentData, nodeTagCountData)
      
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
          core.Document(1l, "title1", "textUrl1", "viewUrl1", Seq(5l, 15l)),
          core.Document(2l, "title2", "textUrl2", "viewUrl2", Seq(15l)),
          core.Document(3l, "title3", "textUrl3", "viewUrl3", Nil))
      
      val tagData = Seq((1l, 5l), (1l, 15l), (2l, 15l))
      val documentData = expectedDocs.map(d => (d.id, d.title, d.textUrl, d.viewUrl))

      val subTreeDataParser = new SubTreeDataParser()
      val documents = subTreeDataParser.createDocuments(documentData, tagData)
      
      documents must be equalTo expectedDocs
    }    
    
    "create Tags from tuples" in {
      val tagData = List((5l, "tag1", 11l, Some(10l)), (5l, "tag1", 11l, Some(20l)),
                         (15l, "tag2", 0l, None))
                         
      val subTreeDataParser = new SubTreeDataParser()
      val tags = subTreeDataParser.createTags(tagData)
      
      val expectedTags = List(core.Tag(5l, "tag1", core.DocumentIdList(Seq(10l, 20l), 11)),
    		  				  core.Tag(15l, "tag2", core.DocumentIdList(Nil, 0)))

      tags must haveTheSameElementsAs(expectedTags)
    }
  }
}