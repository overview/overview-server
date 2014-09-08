/*
 * CsvImportDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import scala.language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration._
import org.overviewproject.database.{ Database, DB }
import org.overviewproject.persistence._
import org.overviewproject.tree.orm.Document
import org.overviewproject.util.{ DocumentConsumer, DocumentProducer, DocumentSetIndexingSession, Logger, SearchIndex }
import org.overviewproject.util.DocumentSetCreationJobStateDescription._
import org.overviewproject.util.Progress._
import org.overviewproject.persistence.orm.finders.DocumentSetFinder
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema.documentSetCreationJobs
import org.overviewproject.tree.orm.finders.FinderById
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder

/**
 * Feed the consumer documents generated from the uploaded file specified by uploadedFileId
 */
class CsvImportDocumentProducer(documentSetId: Long, contentsOid: Long, uploadedFileId: Long, maxDocuments: Int, progAbort: ProgressAbortFn)
  extends DocumentProducer with PersistentDocumentSet {

  private val IndexingTimeout = 3 minutes // Indexing should be complete after clustering is done  
  private val FetchingFraction = 1.0
  private val uploadReader = new UploadReader()
  private var bytesRead = 0l
  private var lastUpdateTime = 0l
  private var jobCancelled: Boolean = false
  private val UpdateInterval = 1000l // only update state every second to reduce locked database access 
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private val documentTagWriter = new DocumentTagWriter(documentSetId)
  private var indexingSession: DocumentSetIndexingSession = _
  
  /** Start parsing the CSV upload and feeding the result to the consumer */
  override def produce(): Int = {
    
    indexingSession = SearchIndex.startDocumentSetIndexingSession(documentSetId)
    
    val uploadedFile = Database.inTransaction {
      EncodedUploadFile.load(uploadedFileId)(Database.currentConnection)
    }
    val reader = uploadReader.reader(contentsOid, uploadedFile)
    val documentSource = new CsvImportSource(org.overviewproject.util.Textify.apply, reader)

    val iterator = documentSource.iterator

    var numberOfSkippedDocuments = 0
    var numberOfParsedDocuments = 0

    while (!jobCancelled && iterator.hasNext) {
      val doc = iterator.next

      if (numberOfParsedDocuments < maxDocuments) {
        val documentId = writeAndCommitDocument(documentSetId, doc)
        indexingSession.indexDocument(documentSetId, documentId, doc.text, doc.title, doc.suppliedId)

        numberOfParsedDocuments += 1
      } else numberOfSkippedDocuments += 1

      reportProgress(uploadReader.bytesRead, uploadedFile.size)

    }
    indexingSession.complete
    
    Database.inTransaction{ documentTagWriter.flush() }

    Await.result(indexingSession.requestsComplete, IndexingTimeout)
    Logger.info("Indexing complete")
    
    updateDocumentSetCounts(documentSetId, numberOfParsedDocuments, numberOfSkippedDocuments)
    
    submitClusteringJob(documentSetId)

    numberOfParsedDocuments
  }

  private def reportProgress(n: Long, size: Long) {
    // The input stream is buffered so new documents may be produced while
    // bytesRead stays the same. Only update if there is a change.
    if (n != bytesRead) {
      bytesRead = n
      val now = scala.compat.Platform.currentTime

      if (now - lastUpdateTime > UpdateInterval) {
        jobCancelled = progAbort(Progress(FetchingFraction * bytesRead / size, Parsing(bytesRead, size)))
        lastUpdateTime = now
      }
    }
  }

  private def writeAndCommitDocument(documentSetId: Long, doc: CsvImportDocument): Long = {
    Database.inTransaction {
      val document = Document(documentSetId, id = ids.next, title = doc.title,
        suppliedId = doc.suppliedId, text = Some(doc.text), url = doc.url)
      DocumentWriter.write(document)
      writeTags(document, doc.tags)
      document.id
    }
  }
  
  private def writeTags(document: Document, tagNames: Set[String]): Unit = {
    val tags = tagNames.map(PersistentTag.findOrCreate(documentSetId, _))
    
    documentTagWriter.write(document, tags)
  }
  
  private def submitClusteringJob(documentSetId: Long): Unit = Database.inTransaction {
    import org.overviewproject.postgres.SquerylEntrypoint._
    val documentSetCreationJobStore = BaseStore(documentSetCreationJobs)
    val documentSetCreationJobFinder = DocumentSetComponentFinder(documentSetCreationJobs)
    
    for {
      documentSet <- DocumentSetFinder.byId(documentSetId).headOption
      job <- documentSetCreationJobFinder.byDocumentSet(documentSetId).headOption
    } {
      val clusteringJob = DocumentSetCreationJob(
       documentSetId = documentSet.id,
       treeTitle = Some(documentSet.title),
       jobType = Recluster,
       suppliedStopWords = job.suppliedStopWords,
       importantWords = job.importantWords,
       splitDocuments = job.splitDocuments,
       state = NotStarted
      )

      documentSetCreationJobStore.insertOrUpdate(clusteringJob)
      documentSetCreationJobStore.delete(job.id)
    }
  }
}
