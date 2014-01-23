package org.overviewproject.clone

import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.{ documentSets, trees }
import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.tree.orm.Tree
import org.overviewproject.persistence.orm.stores.TreeStore
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder

class TreeClonerSpec extends DbSpecification {
  step(setupDb)
  
  
  "TreeCloner" should {
    
    trait TreeCloneContext extends DbTestContext {
      var sourceDocumentSet: DocumentSet = _
      var cloneDocumentSet: DocumentSet = _
      var sourceTree: Tree = _
      var expectedCloneTree: Tree = _
      
      override def setupWithDb = {
        val documentSetStore = BaseStore(documentSets)

        sourceDocumentSet = documentSetStore.insertOrUpdate(DocumentSet(title = "source"))
        cloneDocumentSet = documentSetStore.insertOrUpdate(DocumentSet(title = "clone"))
        
        val sourceIds = new DocumentSetIdGenerator(sourceDocumentSet.id)
        val cloneIds = new DocumentSetIdGenerator(cloneDocumentSet.id)
        
        sourceTree = Tree(sourceIds.next, sourceDocumentSet.id, "title", 100, "lang", "stopwords", "importantwords")
        TreeStore.insert(sourceTree)
        
        expectedCloneTree = sourceTree.copy(id = cloneIds.next, documentSetId = cloneDocumentSet.id)
      }
      
      protected def findClonedTree: Option[Tree] = {
        val finder = DocumentSetComponentFinder(trees)
        
        finder.byDocumentSet(cloneDocumentSet.id).headOption
      }
    }
    
    "clone the tree of a document set" in new TreeCloneContext {
      TreeCloner.clone(sourceDocumentSet.id, cloneDocumentSet.id)
      
      findClonedTree must beSome(expectedCloneTree)
      
    }
  }
  step(shutdownDb)
}