/*
 * DocumentSetCleanerSpec.scala
 *
 * Overview Project
 * Created by Jonas Karlsson, Oct 2012
 */

package org.overviewproject.persistence

import org.overviewproject.test.DbSpecification
import org.overviewproject.test.IdGenerator._
import org.overviewproject.tree.orm.{ Document, DocumentSet, Node, NodeDocument, Tree }
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.postgres.SquerylEntrypoint._

class DocumentSetCleanerSpec extends DbSpecification {
  step(setupDb)

  "DocumentSetCleaner" should {

    trait DocumentSetContext extends DbTestContext {
      var documentSet: DocumentSet = _
      var node: Node = _
      var document: Document = _

      val cleaner = new DocumentSetCleaner

      override def setupWithDb = {
        documentSet = documentSets.insert(DocumentSet(title = "DocumentSetCleanerSpec"))
        val tree = Tree(nextTreeId(documentSet.id), documentSet.id, "tree", 100, "en", "", "")
        node = Node(nextNodeId(documentSet.id), tree.id, documentSet.id, None, "description", 0, Array.empty, false)
        document = Document(documentSet.id, "description")
        val nodeDocument = NodeDocument(node.id, document.id)

        trees.insert(tree)
        nodes.insert(node)
        documents.insert(document)
        nodeDocuments.insert(nodeDocument)

      }
    }

    def findNodeWithDocument(documentSetId: Long): Option[NodeDocument] = {
      val nodeIdQuery = from(nodes)(n =>
        where(n.documentSetId === documentSetId)
          select (n.id))

      from(nodeDocuments)(nd =>
        where(nd.nodeId in nodeIdQuery)
          select (nd)).headOption
    }

    def findNode(nodeId: Long): Option[Node] =
      from(nodes)(n =>
        where(n.id === nodeId)
          select (n)).headOption

    def findDocument(documentSetId: Long): Option[Document] =
      DocumentSetComponentFinder(documents).byDocumentSet(documentSetId).headOption

    def findTree(documentSetId: Long): Option[Tree] =
      DocumentSetComponentFinder(trees).byDocumentSet(documentSetId).headOption

    "delete node related data" in new DocumentSetContext {
      cleaner.clean(documentSet.id)

      findNodeWithDocument(documentSet.id) must beNone
      findNode(node.id) must beNone
      findTree(documentSet.id) must beNone
     
    }

    "delete document related data" in new DocumentSetContext {
      cleaner.clean(documentSet.id)

      val noDocument = findDocument(documentSet.id)

      noDocument must beNone
    }
  }

  step(shutdownDb)
}
