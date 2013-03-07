package org.overviewproject.clone

import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Node
import org.overviewproject.persistence.DocumentSetIdGenerator


class NodeClonerSpec extends DbSpecification {
  step(setupDb)

  trait CloneContext extends DbTestContext {
    val documentIdMapping: Map[Long, Long] = Seq.tabulate[(Long, Long)](10)(i => (i, 100 + i)).toMap
    val documentCache: Array[Long] = documentIdMapping.keys.toArray

    def createTree(documentSetId: Long, parentId: Option[Long], depth: Int): Seq[Node] = {
      if (depth == 0) Nil
      else {
        val child = Node(documentSetId, parentId, "node height: " + depth, 100, documentCache, ids.next)

        Schema.nodes.insert(child)
        child +: createTree(documentSetId, Some(child.id), depth - 1)
      }
    }
    
    var sourceNodes: Seq[Node] = _
    var cloneNodes: Seq[Node] = _
    var nodeIdMapping: Map[Long, Long] = _
    var ids: DocumentSetIdGenerator = _
    
    override def setupWithDb = {
      val documentSetId = insertDocumentSet("NodeClonerSpec")
      val documentSetCloneId = insertDocumentSet("ClonedNodeClonerSpec")
    
      ids = new DocumentSetIdGenerator(documentSetId)
      
      sourceNodes = createTree(documentSetId, None, 10)

      nodeIdMapping = NodeCloner.clone(documentSetId, documentSetCloneId, documentIdMapping)
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
	
	"map document id cache" in new CloneContext {
	  val cloneCache = documentIdMapping.values
	  
	  cloneNodes.head.cachedDocumentIds.toSeq must haveTheSameElementsAs(cloneCache)
	}
  }

  step(shutdownDb)
}