package org.overviewproject.jobhandler.documentset

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm._
import java.sql.Timestamp
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.test.IdGenerator._
import org.overviewproject.database.orm.Schema._
import org.overviewproject.database.orm.finders.DocumentTagFinder
import org.squeryl.Table
import org.overviewproject.database.orm.finders.FindableByDocumentSet
import org.overviewproject.database.orm.finders.NodeDocumentFinder

class DocumentSetDeleterSpec extends DbSpecification {

  step(setupDb)

  "DocumentSetDeleter" should {

    trait DocumentSetContext extends DbTestContext {
      var documentSet: DocumentSet = _

      protected var document: Document = _

      override def setupWithDb = {
        createDocumentSet
      }

      protected def createDocumentSet = {
        documentSet = documentSets.insertOrUpdate(DocumentSet(title = "document set"))
        document = Document(documentSet.id, documentcloudId = Some("dcId"), id = nextDocumentId(documentSet.id))
        documents.insert(document)
      }

      def addClientGeneratedInformation = {
        logEntries.insert(
          LogEntry(documentSetId = documentSet.id,
            userId = 1l,
            date = new Timestamp(0l),
            component = ""))
        val tag = tags.insertOrUpdate(Tag(documentSetId = documentSet.id, name = "", color = "ffffff"))
        documentTags.insert(DocumentTag(document.id, tag.id))
        searchResults.insert(SearchResult(SearchResultState.Complete, documentSet.id, "query"))
      }

      def addClusteringGeneratedInformation = {
        val node = Node(nextNodeId(documentSet.id),
          documentSet.id, None, "", 1, Array(document.id), true)

        nodes.insert(node)
        nodeDocuments.insert(NodeDocument(node.id, document.id))

        documentProcessingErrors.insert(
          DocumentProcessingError(documentSet.id, "url", "message", None, None))
      }

      def findAll[A <: DocumentSetComponent](table: Table[A]): Seq[A] =
        DocumentSetComponentFinder(table).byDocumentSet(documentSet.id).toSeq

      def findAllWithFinder[A](finder: FindableByDocumentSet[A]): Seq[A] =
        finder.byDocumentSet(documentSet.id).toSeq
        
      def findDocumentSet: Option[DocumentSet] = 
        from(documentSets)(ds =>
          where(ds.id === documentSet.id)
          select (ds)).headOption
    }

    "delete client generated information" in new DocumentSetContext {
      addClientGeneratedInformation
      DocumentSetDeleter().deleteClientGeneratedInformation(documentSet.id)

      findAll(logEntries) must beEmpty
      findAllWithFinder(DocumentTagFinder) must beEmpty
      findAll(tags) must beEmpty
      findAll(searchResults) must beEmpty
    }

    "delete clustering generated information" in new DocumentSetContext {
      addClusteringGeneratedInformation
      DocumentSetDeleter().deleteClusteringGeneratedInformation(documentSet.id)

      findAll(nodes) must beEmpty
      findAllWithFinder(NodeDocumentFinder) must beEmpty
      findAll(documentProcessingErrors) must beEmpty
    }

    "delete document set" in new DocumentSetContext {
      DocumentSetDeleter().deleteDocumentSet(documentSet.id)
      
      findAll(documents) must beEmpty
      findAll(documentSetUsers) must beEmpty
      findDocumentSet must beNone
    }

    "delete document set with CSV upload" in new DbTestContext {
      todo
    }

    "delete document set with uploaded PDFs" in new DbTestContext {
      todo
    }
  }

  step(shutdownDb)

}