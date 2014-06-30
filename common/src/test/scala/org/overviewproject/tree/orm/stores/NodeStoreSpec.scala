package org.overviewproject.tree.orm.stores

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ DocumentSet, Node, Tree }
import org.overviewproject.tree.orm.Schema._

class NodeStoreSpec extends DbSpecification {

  step(setupDb)
  
  
  "NodeStore" should {
    
    trait NodeSetup extends DbTestContext {
      var documentSet: DocumentSet = _
      val nodeStore = BaseNodeStore(nodes, trees)
      
      override def setupWithDb = {
        documentSet = documentSets.insert(new DocumentSet(title = "NodeFinderSpec"))
        createTree(documentSet.id, 1l)
        createTree(documentSet.id, 2l)
      }
      
      private def createTree(documentSetId: Long, treeId: Long): Unit = {
        val tree = trees.insert(Tree(treeId, documentSet.id, 0L, "title", 100, "en", "", ""))
        val nodesInTree = Seq.tabulate(10)(n => Node(treeId << 32 | n, tree.id, None, "desc", 1, Array.empty, false ))
        nodes.insert(nodesInTree)
      }
    }
    
    "delete nodes by documentSet" in new NodeSetup {
      nodeStore.deleteByDocumentSet(documentSet.id)
      from(nodes)(n => select(n)).toSeq must beEmpty
    }
  }
  
  step(shutdownDb)

}
