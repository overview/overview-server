package org.overviewproject.fileupload

import scala.language.postfixOps
import scala.concurrent.duration._
import org.overviewproject.database.Database
import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.persistence._
import org.overviewproject.persistence.orm.finders.GroupedProcessedFileFinder
import org.overviewproject.persistence.orm.Schema.files
import org.overviewproject.tree.orm.{ Document, File, GroupedProcessedFile }
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.util.{ DocumentConsumer, DocumentProducer, DocumentSetIndexingSession }
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Parsing
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn }
import org.overviewproject.util.SearchIndex
import scala.concurrent.Await
import org.overviewproject.tree.orm.finders.ResultPage

class FileUploadDocumentProducer(documentSetId: Long, fileGroupId: Long, 
    override protected val consumer: DocumentConsumer, 
    override protected val progAbort: ProgressAbortFn)
    extends PagedDocumentSourceDocumentProducer[GroupedProcessedFile] with PersistentDocumentSet {

  override protected lazy val totalNumberOfDocuments = Database.inTransaction {
    GroupedProcessedFileFinder.byFileGroup(fileGroupId).count
  }
  
  private val IndexingTimeout = 3 minutes
  private val fileStore = new BaseStore(files)
  override protected val PreparingFraction = 0.25
  override protected val FetchingFraction = 0.25
  private val PageSize = 100
  private val ids = new DocumentSetIdGenerator(documentSetId)
  private var indexingSession: DocumentSetIndexingSession = _
  private var fileErrors: Seq[DocumentRetrievalError] = Seq()

  override def produce(): Int = {
    indexingSession = SearchIndex.startDocumentSetIndexingSession(documentSetId)

    val numberOfDocumentsRead = super.produce()
    
    indexingSession.complete
    
    Await.result(indexingSession.requestsComplete, IndexingTimeout)

    updateDocumentSetCounts(documentSetId, numberOfDocumentsRead, 0)

    Database.inTransaction {
      DocRetrievalErrorWriter.write(documentSetId, fileErrors)
    }

    numberOfDocumentsRead
  }

  protected def runQueryForPage(pageNumber: Int)(processDocuments: Iterable[GroupedProcessedFile] => Int): Int = Database.inTransaction {
    val query =  GroupedProcessedFileFinder.byFileGroup(fileGroupId).orderedById
    val result = ResultPage(query, PageSize, pageNumber)
    
    processDocuments(result)
  }
  
  override protected def processDocumentSource(file: GroupedProcessedFile): Unit = {
    file.text.map { text =>
      val documentId = writeAndCommitDocument(documentSetId, file)
      indexingSession.indexDocument(documentSetId, documentId, text, Some(file.name), None)

      consumer.processDocument(documentId, text)
    }.getOrElse {
      val error = file.errorMessage.getOrElse(s"Inconsistent GroupedProcessFile in Database: ${file.id}")
      fileErrors = DocumentRetrievalError(file.name, error) +: fileErrors
    }
  }

  private def writeAndCommitDocument(documentSetId: Long, processedFile: GroupedProcessedFile): Long = {
    val file = fileStore.insertOrUpdate(File(1, processedFile.contentsOid, processedFile.name))

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

