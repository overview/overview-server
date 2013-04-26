package org.overviewproject.persistence

import org.specs2.mutable.Specification

import org.overviewproject.persistence.orm.{Schema, Tag}
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType._

class DocumentTagWriterSpec extends DbSpecification {

  step(setupDb)
  
  "DocumentTagWriter" should {
    
    inExample("write DocumentTags") in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentTagWriterSpec")
      val document = Document(CsvImportDocument, documentSetId, "title", text = Some("text"))
      Schema.documents.insert(document)
      
      val documentTagWriter = new DocumentTagWriter(documentSetId)
      
      val tagNames = Seq("tag1", "tag2", "tag3")
      
      val tagIds = tagNames.map(insertTag(documentSetId, _))
      val tags = Schema.tags.where(t => t.id in tagIds)
      
      documentTagWriter.write(document, tags)
      
      val savedTagIds  = from(Schema.documentTags)(dt => where(dt.documentId === document.id) select(dt.tagId))
      
      savedTagIds.toSeq must haveTheSameElementsAs(tagIds)
    }

    
  }
  
  step(shutdownDb)
}