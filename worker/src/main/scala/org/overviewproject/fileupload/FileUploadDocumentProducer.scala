package org.overviewproject.fileupload

import org.overviewproject.util.DocumentProducer
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn }
import org.overviewproject.tree.orm.File
import org.overviewproject.persistence.orm.finders.FileFinder
import org.overviewproject.util.DocumentConsumer
import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.persistence.DocumentSetIdGenerator
import org.overviewproject.database.Database
import org.overviewproject.tree.orm.Document
import org.overviewproject.persistence.DocumentWriter
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Parsing
import org.overviewproject.persistence.PersistentDocumentSet
import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.persistence.DocRetrievalErrorWriter
import org.overviewproject.util.DocumentSetIndexingSession
import org.overviewproject.util.SearchIndex

class FileUploadDocumentProducer(documentSetId: Long, fileGroupId: Long,
                                 consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer
    with PersistentDocumentSet {

  private val UpdateInterval = 1000l
  private val PreparingFraction = 0.25
  private val FetchingFraction = 0.25
  private var jobCancelled: Boolean = false
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private var indexingSession: DocumentSetIndexingSession = _

  var numberOfDocumentsRead = 0

  def produce(): Unit = {

    indexingSession = SearchIndex.startDocumentSetIndexingSession(documentSetId)

    var lastUpdateTime = 0l
    var fileErrors: Seq[DocumentRetrievalError] = Seq()

    Database.inTransaction {
      val fileCount: Long = FileFinder.byFileGroup(fileGroupId).count
      val files: Iterable[File] = FileFinder.byFileGroup(fileGroupId)
      val iterator = files.iterator

      while (!jobCancelled && iterator.hasNext) {
        val file = iterator.next

        if (file.state == Complete) {
          val documentId = writeAndCommitDocument(documentSetId, file)
          indexingSession.indexDocument(documentSetId, documentId, file.text, Some(file.name), None)
          
          consumer.processDocument(documentId, file.text)
        } else {
          fileErrors = DocumentRetrievalError("", file.name) +: fileErrors
        }

        numberOfDocumentsRead += 1

        lastUpdateTime = reportProgress(numberOfDocumentsRead, fileCount, lastUpdateTime)
      }
    }
    indexingSession.complete

    consumer.productionComplete()
    updateDocumentSetCounts(documentSetId, numberOfDocumentsRead, 0)

    Database.inTransaction {
      DocRetrievalErrorWriter.write(documentSetId, fileErrors)
    }
  }

  private def reportProgress(numberOfDocumentsRead: Long, fileCount: Long, lastUpdateTime: Long): Long = {
    val now = scala.compat.Platform.currentTime

    if (now - lastUpdateTime > UpdateInterval) {
      val fractionComplete = PreparingFraction + FetchingFraction * numberOfDocumentsRead / fileCount
      jobCancelled = progAbort(Progress(fractionComplete, Parsing(numberOfDocumentsRead, fileCount)))

      now
    } else lastUpdateTime
  }

  private def writeAndCommitDocument(documentSetId: Long, file: File): Long = Database.inTransaction {
    val document = Document(id = ids.next, title = Some(file.name), text = Some(file.text))

    DocumentWriter.write(document)

    document.id
  }

}