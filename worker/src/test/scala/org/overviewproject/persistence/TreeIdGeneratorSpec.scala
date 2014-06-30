package org.overviewproject.persistence

import org.overviewproject.test.DbSpecification
import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.tree.orm.{ DocumentSet, Tree }


class TreeIdGeneratorSpec extends DbSpecification {
  step(setupDb)
  
  "TreeIdGenerator" should {
    
    trait DocumentSetSetup extends DbTestContext {
      var documentSet: DocumentSet = _
      
      override def setupWithDb = {
        documentSet = documentSets.insert(DocumentSet(title = "TreeIdGeneratorSpec"))
      }
    }
    
    trait TreeSetup extends DocumentSetSetup {
      val treeIndex = 5
      
      override def setupWithDb = {
        super.setupWithDb
        val tree1 = Tree((documentSet.id << 32) | 1, documentSet.id, 0L, "tree1", 100, "en", "", "")
        val treeN = Tree((documentSet.id << 32) | treeIndex, documentSet.id, 0L, "treeN", 100, "en", "", "")
        
        trees.insert(tree1)
        trees.insert(treeN)
      }
    }
    
    "generate first id if there are no trees" in new DocumentSetSetup {
      val id = TreeIdGenerator.next(documentSet.id)
      
      id must be equalTo ((documentSet.id << 32) | 1)
    }
    
    "generate the id following the largest found tree id" in new TreeSetup {
      val id = TreeIdGenerator.next(documentSet.id)
      
      id must be equalTo ((documentSet.id <<32) | (treeIndex + 1))
    }
  }

  step(shutdownDb)
}
