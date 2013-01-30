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

    "Create document clones" in new DbTestContext {
      val uploadedFileId = insertUploadedFile("contentDisp", "contentType", 100)
      val documentSetId = insertCsvImportDocumentSet(uploadedFileId)

      val uploadedFileCloneId = insertUploadedFile("contentDisp", "contentType", 100)
      val documentSetCloneId = insertCsvImportDocumentSet(uploadedFileCloneId)

      val sourceDocuments = Seq.tabulate(10)(i =>
        Document(CsvImportDocument, documentSetId, text = Some("text-" + i)))
      
      val expectedCloneData = sourceDocuments.map(d => (CsvImportDocument.value, documentSetCloneId, d.text))
      
      Schema.documents.insert(sourceDocuments)
      
      val documentCloner = DocumentCloner.clone(documentSetId, documentSetCloneId)
      val clonedDocuments = Schema.documents.where(d => d.documentSetId === documentSetCloneId)
      val clonedData = clonedDocuments.map(d => (CsvImportDocument.value, d.documentSetId, d.text))
      clonedData must haveTheSameElementsAs(expectedCloneData)
    }
  }
  step(shutdownDb)
}