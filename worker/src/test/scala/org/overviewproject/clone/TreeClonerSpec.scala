package org.overviewproject.clone

import org.overviewproject.persistence.{ DocumentSetIdGenerator, NodeIdGenerator }
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.orm.{ DocumentSet, Node, Tree }

class TreeClonerSpec extends DbSpecification {
  step(setupDb)

  "TreeCloner" should {
    trait TreeCloneContext extends DbTestContext {
      var sourceDocumentSet: DocumentSet = _
      var cloneDocumentSet: DocumentSet = _
      var sourceTree: Tree = _
      var expectedCloneTree: Tree = _

      override def setupWithDb = {
        import org.overviewproject.postgres.SquerylEntrypoint._

        sourceDocumentSet = Schema.documentSets.insertOrUpdate(DocumentSet(title = "source"))
        cloneDocumentSet = Schema.documentSets.insertOrUpdate(DocumentSet(title = "clone"))

        val sourceTreeId = new DocumentSetIdGenerator(sourceDocumentSet.id).next
        val cloneTreeId = new DocumentSetIdGenerator(cloneDocumentSet.id).next

        val sourceRootId = new NodeIdGenerator(sourceTreeId).rootId
        val cloneRootId = new NodeIdGenerator(cloneTreeId).rootId

        Schema.nodes.insert(Node(sourceRootId, sourceRootId, None, "description", 100, true))
        Schema.nodes.insert(Node(cloneRootId, cloneRootId, None, "description", 100, true))

        sourceTree = Schema.trees.insert(Tree(
          id=sourceTreeId,
          documentSetId=sourceDocumentSet.id,
          rootNodeId=sourceRootId,
          jobId=0L,
          title="title",
          documentCount=100,
          lang="lang",
          suppliedStopWords="stopwords",
          importantWords="importantwords"
        ))

        expectedCloneTree = sourceTree.copy(
          id=cloneTreeId,
          documentSetId=cloneDocumentSet.id,
          rootNodeId=cloneRootId
        )
      }

      protected def findClonedTree: Option[Tree] = {
        val finder = DocumentSetComponentFinder(Schema.trees)

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
