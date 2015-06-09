/*
 * CsvImportDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import scala.collection.mutable.Buffer
import scala.concurrent.{Future,blocking}
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.database.{DeprecatedDatabase,BlockingDatabaseProvider}
import org.overviewproject.models.{Document,DocumentTag,Tag}
import org.overviewproject.models.tables.{Documents,DocumentTags,Tags}
import org.overviewproject.persistence.{DocumentSetIdGenerator,EncodedUploadFile,PersistentDocumentSet}
import org.overviewproject.searchindex.TransportIndexClient
import org.overviewproject.util.{BulkDocumentWriter,DocumentProducer,Logger,TagColorList}
import org.overviewproject.util.Progress.{Progress,ProgressAbortFn}
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Parsing

/**
 * Feed the consumer documents generated from the uploaded file specified by uploadedFileId
 */
class CsvImportDocumentProducer(
  documentSetId: Long,
  contentsOid: Long,
  uploadedFileId: Long,
  maxDocuments: Int,
  progAbort: ProgressAbortFn
)
  extends DocumentProducer
  with PersistentDocumentSet
  with BlockingDatabaseProvider
{
  private val FetchingFraction = 1.0
  private var bytesRead = 0l
  private var lastUpdateTime = 0l
  private var jobCancelled: Boolean = false
  private val UpdateInterval = 1000l // only update state every second to reduce locked database access 
  private val ids = new DocumentSetIdGenerator(documentSetId)
  // XXX tagDocumentIds could cause OutOfMemoryError given a malicious document
  private val tagDocumentIds: collection.mutable.Map[String,Buffer[Long]] = collection.mutable.Map()
  private val logger = Logger.forClass(getClass)

  private def await[A](f: Future[A]): A = {
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
  }
  
  /** Start parsing the CSV upload and feeding the result to the consumer */
  override def produce(): Int = {
    val uploadedFile = DeprecatedDatabase.inTransaction {
      EncodedUploadFile.load(uploadedFileId)(DeprecatedDatabase.currentConnection)
    }
    val uploadReader = new UploadReader(contentsOid, uploadedFile.encoding, blockingDatabase)
    val reader = uploadReader.reader
    val documentSource = new CsvImportSource(org.overviewproject.util.Textify.apply, reader)

    await(TransportIndexClient.singleton.addDocumentSet(documentSetId))
    val bulkWriter = BulkDocumentWriter.forDatabaseAndSearchIndex

    val iterator = documentSource.iterator
    var nDocuments = 0

    while (!jobCancelled && iterator.hasNext) {
      val csvDocument = iterator.next
      nDocuments += 1

      if (nDocuments <= maxDocuments) {
        val document = csvDocument.toDocument(ids.next, documentSetId)
        csvDocument.tags.foreach { tagName =>
          tagDocumentIds.getOrElseUpdate(tagName, Buffer()).append(document.id)
        }
        await(bulkWriter.addAndFlushIfNeeded(document))
      }

      reportProgress(uploadReader.bytesRead, uploadedFile.size)
    }

    await(bulkWriter.flush)
    logger.info("Flushed documents")

    flushTagDocumentIds
    logger.info("Flushed tags")

    updateDocumentSetCounts(documentSetId, math.min(maxDocuments, nDocuments), math.max(0, nDocuments - maxDocuments))
    refreshSortedDocumentIds(documentSetId)

    math.min(maxDocuments, nDocuments)
  }

  private def flushTagDocumentIds: Unit = {
    import blockingDatabaseApi._

    val tagInserter = (Tags.map(t => (t.documentSetId, t.name, t.color)) returning Tags)
    val tagsToInsert: Iterable[(Long,String,String)] = tagDocumentIds.keys
      .map { name => (documentSetId, name, TagColorList.forString(name)) }

    val tags = blockingDatabase.run(tagInserter.++=(tagsToInsert))

    tags.foreach { tag =>
      val documentIds = tagDocumentIds(tag.name)
      val documentTags = documentIds.map(DocumentTag(_, tag.id))
      blockingDatabase.run(DocumentTags.++=(documentTags))
    }
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
}
