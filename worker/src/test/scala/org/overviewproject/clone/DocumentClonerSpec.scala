package org.overviewproject.clone

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType._
import persistence.Schema

class DocumentClonerSpec extends DbSpecification {
  step(setupDb)

  "DocumentCloner" should {

    trait CloneContext extends DbTestContext {
      var documentSetId: Long = _
      var documentSetCloneId: Long = _
      var expectedCloneData: Seq[(String, Long, String)] = _
      var documentCloner: DocumentCloner = _
      var sourceDocuments: Seq[Document] = _
      var clonedDocuments: Seq[Document] = _

      def createCsvImportDocumentSet: Long = {
        val uploadedFileId = insertUploadedFile("contentDisp", "contentType", 100)
        insertCsvImportDocumentSet(uploadedFileId)
      }

      override def setupWithDb = {
        documentSetId = createCsvImportDocumentSet
        documentSetCloneId = createCsvImportDocumentSet

        val documents = Seq.tabulate(10)(i =>
          Document(CsvImportDocument, documentSetId, text = Some("text-" + i)))
        Schema.documents.insert(documents)
        sourceDocuments = Schema.documents.where(d => d.documentSetId === documentSetId).toSeq

        expectedCloneData = sourceDocuments.map(d => (CsvImportDocument.value, documentSetCloneId, d.text.get))

        documentCloner = DocumentCloner.clone(documentSetId, documentSetCloneId)
        clonedDocuments = Schema.documents.where(d => d.documentSetId === documentSetCloneId).toSeq

      }
    }

    "Create document clones" in new CloneContext {
      val clonedData = clonedDocuments.map(d => (CsvImportDocument.value, d.documentSetId, d.text.get))
      clonedData must haveTheSameElementsAs(expectedCloneData)
    }

    "Map source document ids to clone document ids" in new CloneContext {
      val mappedIds = sourceDocuments.flatMap(d => documentCloner.getCloneId(d.id))

      mappedIds must haveTheSameElementsAs(clonedDocuments.map(_.id))
    }
  }
  step(shutdownDb)
}