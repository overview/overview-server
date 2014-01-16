package org.overviewproject.fileupload

import scala.language.postfixOps
import scala.concurrent.duration._
import org.overviewproject.database.Database
import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.persistence._
import org.overviewproject.persistence.orm.finders.FileFinder
import org.overviewproject.persistence.orm.Schema.files
import org.overviewproject.tree.orm.{ Document, File, GroupedProcessedFile }
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.util.{ DocumentConsumer, DocumentProducer, DocumentSetIndexingSession }
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Parsing
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn }
import org.overviewproject.util.SearchIndex
import scala.concurrent.Await

class FileUploadDocumentProducer(documentSetId: Long, fileGroupId: Long,
                                 consumer: DocumentConsumer, progAbort: ProgressAbortFn) extends DocumentProducer
    with PersistentDocumentSet {

  private val IndexingTimeout = 3 minutes
  private val fileStore = new BaseStore(files)
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
      val files: Iterable[GroupedProcessedFile] = FileFinder.byFileGroup(fileGroupId)
      val iterator = files.iterator

      while (!jobCancelled && iterator.hasNext) {
        val file = iterator.next

        file.text.map { text =>
          val documentId = writeAndCommitDocument(documentSetId, file)
          indexingSession.indexDocument(documentSetId, documentId, text, Some(file.name), None)

          consumer.processDocument(documentId, text)
        } getOrElse {
          val error = file.errorMessage.getOrElse(s"Inconsistent GroupedProcessFile in Database: ${file.id}")
          fileErrors = DocumentRetrievalError(file.name, error) +: fileErrors
        }

        numberOfDocumentsRead += 1

        lastUpdateTime = reportProgress(numberOfDocumentsRead, fileCount, lastUpdateTime)
      }
    }
    indexingSession.complete

    consumer.productionComplete()

    Await.result(indexingSession.requestsComplete, IndexingTimeout)

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

  private def writeAndCommitDocument(documentSetId: Long, processedFile: GroupedProcessedFile): Long = Database.inTransaction {
    val file = fileStore.insertOrUpdate(File(1, processedFile.contentsOid))

    val document = Document(
      documentSetId,
      id = ids.next,
      title = Some(processedFile.name),
      text = processedFile.text,
      contentLength = Some(processedFile.size),
      fileId = Some(file.id))

    DocumentWriter.write(document)

    document.id
  }

}