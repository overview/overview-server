package org.overviewproject.clone

import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType._


class DocumentClonerSpec extends DbSpecification {
  step(setupDb)

  "DocumentCloner" should {

    trait CloneContext extends DbTestContext {
      var documentSetId: Long = _
      var documentSetCloneId: Long = _
      var expectedCloneData: Seq[(String, Long, String)] = _
      var documentIdMapping: Map[Long, Long] = _
      var sourceDocuments: Seq[Document] = _
      var clonedDocuments: Seq[Document] = _
      var ids: DocumentSetIdGenerator = _
      
      def createCsvImportDocumentSet: Long = {
        val uploadedFileId = insertUploadedFile("contentDisp", "contentType", 100)
        insertCsvImportDocumentSet(uploadedFileId)
      }

      def documents: Seq[Document] = Seq.tabulate(10)(i =>
          Document(CsvImportDocument, documentSetId, text = Some("text-" + i), id = ids.next))

      override def setupWithDb = {
        documentSetId = createCsvImportDocumentSet
        documentSetCloneId = createCsvImportDocumentSet
        ids = new DocumentSetIdGenerator(documentSetId)
        
        Schema.documents.insert(documents)
        sourceDocuments = Schema.documents.where(d => d.documentSetId === documentSetId).toSeq

        expectedCloneData = sourceDocuments.map(d => (CsvImportDocument.value, documentSetCloneId, d.text.get))

        DocumentCloner.clone(documentSetId, documentSetCloneId)
        clonedDocuments = Schema.documents.where(d => d.documentSetId === documentSetCloneId).toSeq

      }
    }
    
    trait DocumentsWithLargeIds extends CloneContext {
      override def documents: Seq[Document] = Seq(Document(CsvImportDocument, documentSetId, text = Some("text"), id = ids.next + 0xFFFFFFFAl))
    }

    "Create document clones" in new CloneContext {
      val clonedData = clonedDocuments.map(d => (CsvImportDocument.value, d.documentSetId, d.text.get))
      clonedData must haveTheSameElementsAs(expectedCloneData)
    }
    
    "Create clones with ids matching source ids" in new CloneContext {
      val sourceIndeces = sourceDocuments.map(d => (d.id << 32) >> 32)
      val cloneIndeces = clonedDocuments.map(d => (d.id << 32) >> 32)
      
      sourceIndeces must haveTheSameElementsAs(cloneIndeces)
    }

    "create clones with documentSetId encoded in id" in new DocumentsWithLargeIds {
      val highOrderBits = clonedDocuments.map(_.id >> 32)
      
      highOrderBits.distinct must contain(documentSetCloneId).only
    }
  }
  step(shutdownDb)
}