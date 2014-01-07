package org.overviewproject.jobhandler.documentset

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm._
import java.sql.Timestamp
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.test.IdGenerator._
import org.overviewproject.database.orm.Schema._
import org.overviewproject.database.orm.finders.DocumentTagFinder

class DocumentSetDeleterSpec extends DbSpecification {
  
  step(setupDb)
  
  "DocumentSetDeleter" should {
    
    
    inExample("delete client generated information") in new DbTestContext {
      val documentSet = documentSets.insertOrUpdate(DocumentSet(title =  "document set"))
      val documentSetId = documentSet.id
      val document = Document(documentSetId, documentcloudId = Some("dcId"), id = nextDocumentId(documentSetId))
      documents.insert(document)
      
      val logEntry = logEntries.insertOrUpdate(
          LogEntry(documentSetId = documentSet.id, userId = 1l, date = new Timestamp(0l), component = ""))
      val tag = tags.insertOrUpdate(Tag(documentSetId = documentSet.id, name = "", color = "ffffff"))
      documentTags.insert(DocumentTag(document.id, tag.id))
      searchResults.insert(SearchResult(SearchResultState.Complete, documentSet.id, "query"))
      
      DocumentSetDeleter().deleteClientGeneratedInformation(documentSet.id)

      val remainingLogEntries = DocumentSetComponentFinder(logEntries).byDocumentSet(documentSet.id)
      val remainingDocumentTags = DocumentTagFinder.byDocumentSet(documentSet.id)
      val remainingTags = DocumentSetComponentFinder(tags).byDocumentSet(documentSet.id)
      val remainingSearchResults = DocumentSetComponentFinder(searchResults).byDocumentSet(documentSet.id)
      
      remainingLogEntries must beEmpty
      remainingDocumentTags must beEmpty
      remainingTags must beEmpty
      remainingSearchResults must beEmpty
    }
  }
  
  
  step(shutdownDb)

}