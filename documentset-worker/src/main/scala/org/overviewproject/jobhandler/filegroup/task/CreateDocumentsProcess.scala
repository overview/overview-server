package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import org.overviewproject.util.SearchIndex
import org.overviewproject.util.DocumentSetIndexingSession
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

trait CreateDocumentsProcess {

  protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator

  def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean): FileGroupTaskStep = {

    val indexingSession = searchIndex.startDocumentSetIndexingSession(documentSetId)

    if (!splitDocuments) CreateDocumentsFromFileQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId), indexingSession)
    else CreateDocumentsFromPagesQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId), indexingSession)

  }

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

    protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document]
  }

  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int,
                                                      documentIdGenerator: DocumentIdGenerator,
                                                      indexingSession: DocumentSetIndexingSession)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, indexingSession) {

    override protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document] =
      files.map(createDocument(documentSetId, _))

    private def createDocument(documentSetId: Long, file: File) = {
      val pages = createDocumentsProcessStorage.findFilePageData(file.id)

      val text = pages.foldLeft("")((text, page) => text + page._3.getOrElse(""))

      Document(documentSetId,
        id = documentIdGenerator.nextId,
        title = Some(file.name),
        text = Some(text),
        fileId = Some(file.id))
    }
  }

  private case class CreateDocumentsFromPagesQueryPage(documentSetId: Long, queryPage: Int,
                                                       documentIdGenerator: DocumentIdGenerator,
                                                       indexingSession: DocumentSetIndexingSession)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, indexingSession) {

    override protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document] =
      files.flatMap(createDocumentsFromPages(documentSetId, _))

    private def createDocumentsFromPages(documentSetId: Long, file: File): Iterable[Document] = {
      val pages = createDocumentsProcessStorage.findFilePageData(file.id)

      pages.map { p =>
        Document(documentSetId,
          id = documentIdGenerator.nextId,
          title = Some(file.name),
          text = p._3,
          fileId = Some(file.id),
          pageId = Some(p._1),
          pageNumber = Some(p._2))
      }
    }
  }

  protected val searchIndex: SearchIndex
  protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage

  protected trait CreateDocumentsProcessStorage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageData(fileId: Long): Iterable[(Long, Int, Option[String])]
    def writeDocuments(documents: Iterable[Document]): Unit
    def saveDocumentCount(documentSetId: Long): Unit
    def deleteTempFiles(documentSetId: Long): Unit
  }
}