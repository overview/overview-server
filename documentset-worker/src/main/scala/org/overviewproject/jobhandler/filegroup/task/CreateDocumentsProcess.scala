package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.searchindex.ElasticSearchIndexClient
import scala.concurrent.Future
import org.overviewproject.util.Logger

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

  protected val ElasticSearchRequestTimeout = 1 minutes

  protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator

  /** Create the first step in the process */
  def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean,
                               progressReporter: ActorRef): FileGroupTaskStep = {

    Logger.info(s"[$documentSetId] create index")
    await(searchIndex.addDocumentSet(documentSetId))
    Logger.info("s[$documentSetId] index created")
    if (!splitDocuments) CreateDocumentsFromFileQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId),
      progressReporter)
    else CreateDocumentsFromPagesQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId),
      progressReporter)

  }

  private def await[T](f: Future[T]): T = Await.result(f, ElasticSearchRequestTimeout)

  // Parent class for steps in the process of creating documents.
  // Get one page of the query result reading Files, create Documents and add them to the SearchIndex
  // If there are no more results, update the document count, and delete the TempDocumentSetFiles entries
  // used to find the files.
  private abstract class CreateAndIndexDocument(documentSetId: Long, queryPage: Int,
                                                documentIdGenerator: DocumentIdGenerator,
                                                progressReporter: ActorRef) extends FileGroupTaskStep {
    protected val IndexingTimeout = 3 minutes

    override def execute: FileGroupTaskStep = {
      Logger.info(s"[$documentSetId] Find files")
      val files = createDocumentsProcessStorage.findFilesQueryPage(documentSetId, queryPage)

      if (files.nonEmpty) {
        files.foreach(reportStartTask)
        Logger.info(s"[$documentSetId] create documents")
        val documents = createDocumentsFromFiles(files)
        Logger.info(s"[$documentSetId] save documents")
        createDocumentsProcessStorage.writeDocuments(documents)
        Logger.info(s"[$documentSetId] index documents")
        indexDocuments(documents)
        Logger.info(s"[$documentSetId] report status")
        files.foreach(reportCompleteTask)
        Logger.info(s"[$documentSetId] Goto next step")
        CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1, documentIdGenerator, progressReporter)
      } else {
        createDocumentsProcessStorage.saveDocumentCount(documentSetId)
        createDocumentsProcessStorage.deleteTempFiles(documentSetId)

        waitForIndexCompletion

        CreateDocumentsProcessComplete(documentSetId)
      }
    }

    private def indexDocuments(documents: Iterable[Document]): Unit = await(searchIndex.addDocuments(documents))
    private def waitForIndexCompletion: Unit = await(searchIndex.refresh)
    
    // Document creation is handled by subclasses, depending on value of splitDocuments
    protected def createDocumentsFromFiles(files: Iterable[File]): Iterable[Document]

    private def reportStartTask(file: File): Unit =
      progressReporter ! StartTask(documentSetId, file.id)

    private def reportCompleteTask(file: File): Unit =
      progressReporter ! CompleteTask(documentSetId, file.id)
  }

  // Create one Document per File by concatenating the text of all Pages
  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int,
                                                      documentIdGenerator: DocumentIdGenerator,
                                                      progressReporter: ActorRef)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, progressReporter) {

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
                                                       progressReporter: ActorRef)
      extends CreateAndIndexDocument(documentSetId, queryPage, documentIdGenerator, progressReporter) {

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

  protected val searchIndex: ElasticSearchIndexClient
  protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage

  protected trait CreateDocumentsProcessStorage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageText(fileId: Long): Iterable[PageText]
    def writeDocuments(documents: Iterable[Document]): Unit
    def saveDocumentCount(documentSetId: Long): Unit
    def deleteTempFiles(documentSetId: Long): Unit
  }
}