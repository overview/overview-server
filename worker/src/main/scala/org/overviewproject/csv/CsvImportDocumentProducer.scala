/*
 * CsvImportDocumentProducer.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import org.overviewproject.database.{ Database, DB }
import org.overviewproject.persistence.{ DocumentWriter, EncodedUploadFile, PersistentDocumentSet }
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType.{ CsvImportDocument => CsvImportDocumentType }
import org.overviewproject.util.{ DocumentConsumer, DocumentProducer }
import org.overviewproject.util.DocumentSetCreationJobStateDescription._
import org.overviewproject.util.Progress._

/**
 * Feed the consumer documents generated from the uploaded file specified by uploadedFileId
 */
class CsvImportDocumentProducer(documentSetId: Long, contentsOid: Long, uploadedFileId: Long, consumer: DocumentConsumer, maxDocuments: Int, progAbort: ProgressAbortFn)
  extends DocumentProducer with PersistentDocumentSet {

  private val FetchingFraction = 0.5
  private val uploadReader = new UploadReader()
  private var bytesRead = 0l
  private var lastUpdateTime = 0l
  private var jobCancelled: Boolean = false
  private val UpdateInterval = 1000l // only update state every second to reduce locked database access 

  /** Start parsing the CSV upload and feeding the result to the consumer */
  def produce() {
    val uploadedFile = Database.inTransaction {
      EncodedUploadFile.load(uploadedFileId)(Database.currentConnection)
    }
    val reader = uploadReader.reader(contentsOid, uploadedFile)
    val documentSource = new CsvImportSource(reader)

    val iterator = documentSource.iterator

    var numberOfSkippedDocuments = 0
    var numberOfParsedDocuments = 0

    while (!jobCancelled && iterator.hasNext) {
      val doc = iterator.next

      if (numberOfParsedDocuments < maxDocuments) {
        val documentId = writeAndCommitDocument(documentSetId, doc)
        consumer.processDocument(documentId, doc.text)
        numberOfParsedDocuments += 1
      } else numberOfSkippedDocuments += 1

      reportProgress(uploadReader.bytesRead, uploadedFile.size)

    }

    consumer.productionComplete()
    updateDocumentSetCounts(documentSetId, numberOfParsedDocuments, numberOfSkippedDocuments)
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
      val document = Document(CsvImportDocumentType, documentSetId, title = doc.title,
        suppliedId = doc.suppliedId, text = Some(doc.text), url = doc.url)
      DocumentWriter.write(document)
      document.id
    }
  }
}