package org.overviewproject.persistence


import org.overviewproject.persistence.orm.{ Schema, Tag }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ Document, DocumentSet}

class DocumentTagWriterSpec extends DbSpecification {

  step(setupDb)
  
  "DocumentTagWriter" should {
    
    inExample("write DocumentTags in batches") in new DbTestContext {
      val documentSet = Schema.documentSets.insert(DocumentSet(title = "DocumentTagWriterSpec"))
      val document = Document(documentSet.id, "title", text = Some("text"))
      Schema.documents.insert(document)
      
      val documentTagWriter = new DocumentTagWriter(documentSet.id)
      
      val tagNames = Seq("tag1", "tag2", "tag3")
      
      val savedTags = tagNames.map(n => Schema.tags.insert(Tag(documentSetId = documentSet.id, name = n, color = "ffffff")))
      
      documentTagWriter.write(document, savedTags)
      
      val tagsBeforeBatchFilled = from(Schema.documentTags)(dt => where(dt.documentId === document.id) select(dt.tagId))
      
      tagsBeforeBatchFilled.toSeq must beEmpty
      
      documentTagWriter.flush()
      
      val savedTagIds = from(Schema.documentTags)(dt => where(dt.documentId === document.id) select(dt.tagId))
      savedTagIds.toSeq must haveTheSameElementsAs(savedTags.map(_.id))
    }

    
  }
  
  step(shutdownDb)
}