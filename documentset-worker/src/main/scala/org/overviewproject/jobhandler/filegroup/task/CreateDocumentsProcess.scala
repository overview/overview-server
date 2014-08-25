package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import org.overviewproject.util.DocumentSetIndexingSession
import org.overviewproject.util.SearchIndex


/**
 * Creates [[Document]]s from [[File]]s. [[File]]s are read from the database in chunks, with each step 
 * in the process converting one page of the query result.
 * Depending on the value of the `splitDocuments` parameter, one [[Document]] is created per file, or
 * one [[Document]] is created for each [[Page]] of a [[File]].
 * 
 * Only one [[CreateDocumentsProcess]] can be running in order to guarantee unique [[Document]] ids.
 * 
 */
trait CreateDocumentsProcess {

  protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator

  /** Create the first step in the process */
  def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean): FileGroupTaskStep = {

    val indexingSession = searchIndex.startDocumentSetIndexingSession(documentSetId)

    if (!splitDocuments) CreateDocumentsFromFileQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId), indexingSession)
    else CreateDocumentsFromPagesQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId), indexingSession)

  }

  
  // Parent class for steps in the process of creating documents.
  // Get one page of the query result reading Files, create Documents and add them to the SearchIndex
  // If there are no more results, update the document count, and delete the TempDocumentSetFiles entries
  // used to find the files.
  private abstract class CreateAndIndexDocument(documentSetId: Long, queryPage: Int,
                                                documentIdGenerator: DocumentIdGenerator,
                                                indexingSession: DocumentSetIndexingSession) extends FileGroupTaskStep {
    protected val IndexingTimeout = 3 minutes

    override def execute: FileGroupTaskStep = {
      val files = createDocumentsProcessStorage.findFilesQueryPage(documentSetId, queryPage)

      if (files.nonEmpty) {
        val documents = createDocumentsFromFiles(files)
        createDocumentsProcessStorage.writeDocuments(documents)

        indexDocuments(documentSetId, documents)

        CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1, documentIdGenerator, indexingSession)
      } else {
        createDocumentsProcessStorage.saveDocumentCount(documentSetId)
        createDocumentsProcessStorage.deleteTempFiles(documentSetId)

        indexingSession.complete
        Await.result(indexingSession.requestsComplete, IndexingTimeout)
        CreateDocumentsProcessComplete(documentSetId)
      }
    }

    protected def indexDocuments(documentSetId: Long, documents: Iterable[Document]): Unit =
      documents.map { d =>
        indexingSession.indexDocument(documentSetId, d.id, d.text.getOrElse(""), d.title, d.suppliedId)
      }

    // Document creation is handled by subclasses, depending on value of splitDocuments
    protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document]
  }

  // Create one Document per File by concatenating the text of all Pages
  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int,
                                                      documentIdGenerator: DocumentIdGenerator,
                                                      indexingSession: DocumentSetIndexingSession)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, indexingSession) {

    override protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document] =
      files.map(createDocument(documentSetId, _))

    private def createDocument(documentSetId: Long, file: File) = {
      val pages = createDocumentsProcessStorage.findFilePageText(file.id)

      val text = pages.foldLeft("")((text, page) => text + page.text.getOrElse(""))

      Document(documentSetId,
        id = documentIdGenerator.nextId,
        title = Some(file.name),
        text = Some(text),
        fileId = Some(file.id))
    }
  }

  // Create one Document for each Page in a File.
  private case class CreateDocumentsFromPagesQueryPage(documentSetId: Long, queryPage: Int,
                                                       documentIdGenerator: DocumentIdGenerator,
                                                       indexingSession: DocumentSetIndexingSession)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, indexingSession) {

    override protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document] =
      files.flatMap(createDocumentsFromPages(documentSetId, _))

    private def createDocumentsFromPages(documentSetId: Long, file: File): Iterable[Document] = {
      val pages = createDocumentsProcessStorage.findFilePageText(file.id)

      pages.map { p =>
        Document(documentSetId,
          id = documentIdGenerator.nextId,
          title = Some(file.name),
          text = p.text,
          fileId = Some(file.id),
          pageId = Some(p.id),
          pageNumber = Some(p.number))
      }
    }
  }

  protected val searchIndex: SearchIndex
  protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage

  protected trait CreateDocumentsProcessStorage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageText(fileId: Long): Iterable[PageText]
    def writeDocuments(documents: Iterable[Document]): Unit
    def saveDocumentCount(documentSetId: Long): Unit
    def deleteTempFiles(documentSetId: Long): Unit
  }
}