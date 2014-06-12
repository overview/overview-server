package org.overviewproject.fileupload

import scala.language.postfixOps
import scala.concurrent.Await
import scala.concurrent.duration._

import org.overviewproject.database.Database
import org.overviewproject.documentcloud.DocumentRetrievalError
import org.overviewproject.persistence._
import org.overviewproject.persistence.orm.finders.FileFinder
import org.overviewproject.persistence.orm.finders.PageFinder
import org.overviewproject.persistence.orm.finders.TempDocumentSetFileFinder
import org.overviewproject.tree.orm.{ Document, File, TempDocumentSetFile }
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.Page
import org.overviewproject.tree.orm.TempDocumentSetFile
import org.overviewproject.tree.orm.finders.ResultPage
import org.overviewproject.util.{ DocumentConsumer, DocumentSetIndexingSession }
import org.overviewproject.util.Progress.ProgressAbortFn
import org.overviewproject.util.SearchIndex


/**
 * Produce documents based on uploaded files stored in `Pages`.
 * @param splitDocuments if `true`, create one document for each `Page`. Otherwise 
 *   combine the page text of one file into one document.
 */
class FileUploadDocumentProducer(documentSetId: Long, fileGroupId: Long, splitDocuments: Boolean,
                                 override protected val consumer: DocumentConsumer,
                                 override protected val progAbort: ProgressAbortFn)
    extends PagedDocumentSourceDocumentProducer[TempDocumentSetFile] with PersistentDocumentSet {

  private case class SimplePage(id: Long, pageNumber: Int, text: Option[String], textErrorMessage: Option[String])
  
  override protected lazy val totalNumberOfDocuments =
    if (splitDocuments) countPages
    else countFiles

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

  /**
   * Generates `Documents` from the `File` specified by the input `documentSetFile`.
   * `Documents` are created by using the text extracted and stored in the `Pages` associated with the `File`.
   * If the text extraction failed, the `Page` object will have an error message instead of document text.
   * `Document`s are created only for successful text extractions. For failed extractions, `DocumentRetrievalError`s
   * are generated.
   */
  override protected def processDocumentSource(documentSetFile: TempDocumentSetFile): Int = {
    val (file, filePages) = findFileWithPages(documentSetFile.fileId)

    if (!splitDocuments) {
      val document: Either[String, Document] = createDocumentFromPages(file, filePages)

      document.fold(recordError(file.name, _), produceDocument)
      1
    } else {
      val documents: Iterable[Either[String, Document]] = createDocumentsFromPages(file, filePages)

      documents.foreach { _.fold(recordError(file.name, _), produceDocument) }
      documents.count(_.isRight)
    }
  }

  // Don't read the Page binary data, or we may run out of memory
  // It would be more efficient to not read all pages at once, if possible.
  private def findFileWithPages(fileId: Long): (File, Iterable[SimplePage]) = Database.inTransaction {
    val file = FileFinder.byId(fileId).headOption.get
    val pageInfo = PageFinder.byFileId(fileId).withoutData
    val simplePages = pageInfo.map(SimplePage tupled)
    (file, simplePages.view)
  }

  private def recordError(fileName: String, error: String): Unit = {
    fileErrors = DocumentRetrievalError(fileName, error) +: fileErrors
  }

  
  // Create one document from the pages. If one of the pages has an error, the entire document 
  // is assumed to have failed. The result is either a Document with the combined text of all the documents
  // or a string combining the error messages of all pages.
  private def createDocumentFromPages(file: File, pages: Iterable[SimplePage]): Either[String, Document] = {
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

  // For each page, return a Document containing the page text, or a string with the Page error message
  private def createDocumentsFromPages(file: File, pages: Iterable[SimplePage]): Iterable[Either[String, Document]] = {

    pages.map { p =>
      p.text.toRight(pageError(p)).right.map { text =>
        Document(documentSetId, id = ids.next, title = Some(file.name), text = Some(text), fileId = Some(file.id),
          pageNumber = Some(p.pageNumber), pageId = Some(p.id))
      }
    }
  }

  private def pageError(page: SimplePage): String =
      page.textErrorMessage.getOrElse(s"text extraction failed for page ${page.pageNumber}")

  private def produceDocument(document: Document): Unit = {
    Database.inTransaction { DocumentWriter.write(document) }
    val documentText = document.text.get

    indexingSession.indexDocument(documentSetId, document.id, documentText, Some(document.title.get), None)

    consumer.processDocument(document.id, documentText)
  }

  private def countFiles: Long = Database.inTransaction {
    TempDocumentSetFileFinder.byDocumentSet(documentSetId).count
  }

  private def countPages: Long = Database.inTransaction {
    PageFinder.byDocumentSet(documentSetId).count
  }
}

