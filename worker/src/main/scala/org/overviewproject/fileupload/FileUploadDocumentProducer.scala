package org.overviewproject.fileupload

import scala.language.postfixOps
import scala.concurrent.duration._
import org.overviewproject.database.Database
import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.persistence._
import org.overviewproject.persistence.orm.finders.GroupedProcessedFileFinder
import org.overviewproject.persistence.orm.Schema.tempDocumentSetFiles
import org.overviewproject.tree.orm.{ Document, File, TempDocumentSetFile }
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.util.{ DocumentConsumer, DocumentProducer, DocumentSetIndexingSession }
import org.overviewproject.util.DocumentSetCreationJobStateDescription.Parsing
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn }
import org.overviewproject.util.SearchIndex
import scala.concurrent.Await
import org.overviewproject.tree.orm.finders.ResultPage
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.persistence.orm.finders.TempDocumentSetFileFinder
import org.overviewproject.persistence.orm.finders.PageFinder
import org.overviewproject.persistence.orm.finders.FileFinder

class FileUploadDocumentProducer(documentSetId: Long, fileGroupId: Long,
                                 override protected val consumer: DocumentConsumer,
                                 override protected val progAbort: ProgressAbortFn)
    extends PagedDocumentSourceDocumentProducer[TempDocumentSetFile] with PersistentDocumentSet {

  override protected lazy val totalNumberOfDocuments = Database.inTransaction {
    TempDocumentSetFileFinder.byDocumentSet(documentSetId).count
  }

  private val IndexingTimeout = 3 minutes
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

  protected def runQueryForPage(pageNumber: Int)(processDocuments: Iterable[TempDocumentSetFile] => Int): Int = Database.inTransaction {
    val query = TempDocumentSetFileFinder.byDocumentSet(documentSetId).orderByFileIds
    val result = ResultPage(query, PageSize, pageNumber)

    processDocuments(result)
  }

  override protected def processDocumentSource(documentSetFile: TempDocumentSetFile): Unit = {
    val (file, filePages) = findFileWithPages(documentSetFile.fileId)
    val document = createDocumentFromPages(file, filePages)

    document.fold(recordError(file.name, _), produceDocument)
  }

  private def findFileWithPages(fileId: Long): (File, Seq[Page]) = Database.inTransaction {
    val file = FileFinder.byId(fileId).headOption.get
    val pages = PageFinder.byFileId(fileId).toSeq
    (file, pages)
  }

  private def recordError(fileName: String, error: String): Unit = {
    fileErrors = DocumentRetrievalError(fileName, error) +: fileErrors
  }

  private def createDocumentFromPages(file: File, pages: Seq[Page]): Either[String, Document] = {
    def pageError(page: Page): String =
      page.textErrorMessage.getOrElse(s"text extraction failed for page ${page.pageNumber}")

    val documentText = pages.foldLeft[Either[Seq[String], String]](Right(""))((text, page) => {
      val pageText: Either[String, String] = page.text.toRight(pageError(page))

      pageText.fold(
        error => Left(text.left.getOrElse(Seq.empty) :+ error),
        t => text.right.map(_ + t))
    })

    documentText.fold(
      errors => Left(errors.mkString("\n")),
      text => Right(Document(
        documentSetId,
        id = ids.next,
        title = Some(file.name),
        text = Some(text),
        fileId = Some(file.id))))

  }

  private def produceDocument(document: Document): Unit = {
    Database.inTransaction { DocumentWriter.write(document) }
    val documentText = document.text.get

    indexingSession.indexDocument(documentSetId, document.id, documentText, Some(document.title.get), None)

    consumer.processDocument(document.id, documentText)
  }

}

