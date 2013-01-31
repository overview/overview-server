package org.overviewproject.clone

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Node
import persistence.Schema

class NodeClonerSpec extends DbSpecification {
  step(setupDb)

  trait CloneContext extends DbTestContext {
    def createTree(documentSetId: Long, parentId: Option[Long], depth: Int): Seq[Node] = {
      if (depth == 0) Nil
      else {
        val child = Node(documentSetId, parentId, "node height: " + depth, 0, Array())

        Schema.nodes.insert(child)
        child +: createTree(documentSetId, Some(child.id), depth - 1)
      }
    }
    
    var sourceNodes: Seq[Node] = _
    var cloneNodes: Seq[Node] = _
    var nodeIdMapping: Map[Long, Long] = _
    
    override def setupWithDb = {
      val documentSetId = insertDocumentSet("NodeClonerSpec")
      val documentSetCloneId = insertDocumentSet("ClonedNodeClonerSpec")
      sourceNodes = createTree(documentSetId, None, 10)

      nodeIdMapping = NodeCloner.clone(documentSetId, documentSetCloneId)
      cloneNodes = Schema.nodes.where(n => n.documentSetId === documentSetCloneId).toSeq
    }
  }

  "NodeCloner" should {

	"create node clones" in new CloneContext {
      cloneNodes.sortBy(_.id).map(_.description) must be equalTo sourceNodes.map(_.description)
    }

	"map source node ids to clone node ids" in new CloneContext {
	  val mappedIds = sourceNodes.flatMap(n => nodeIdMapping.get(n.id))
	  
	  mappedIds must be equalTo(cloneNodes.sortBy(_.id).map(_.id))
	}
  }

  step(shutdownDb)
}