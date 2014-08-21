package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File

trait CreateDocumentsProcess {

  protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator

  def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean): FileGroupTaskStep =
    if (!splitDocuments) CreateDocumentsFromFileQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId))
    else CreateDocumentsFromPagesQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId))

  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int, documentIdGenerator: DocumentIdGenerator) extends FileGroupTaskStep {

    override def execute: FileGroupTaskStep = {
      val files = createDocumentsProcessStorage.findFilesQueryPage(documentSetId, queryPage)

      if (files.nonEmpty) {
        val documents = files.map(createDocument(documentSetId, _))
        createDocumentsProcessStorage.writeDocuments(documents)

        CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1, documentIdGenerator)
      } else {
        createDocumentsProcessStorage.saveDocumentCount(documentSetId)
        createDocumentsProcessStorage.deleteTempFiles(documentSetId)
        CreateDocumentsProcessComplete(documentSetId)
      }
    }

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

  private case class CreateDocumentsFromPagesQueryPage(documentSetId: Long, queryPage: Int, documentIdGenerator: DocumentIdGenerator) extends FileGroupTaskStep {

    override def execute: FileGroupTaskStep = {
      val files = createDocumentsProcessStorage.findFilesQueryPage(documentSetId, queryPage)

      if (files.nonEmpty) {
        val documents = files.flatMap(createDocumentsFromPages(documentSetId, _))
        createDocumentsProcessStorage.writeDocuments(documents)

        CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1, documentIdGenerator)
      } else {
        createDocumentsProcessStorage.saveDocumentCount(documentSetId)
        createDocumentsProcessStorage.deleteTempFiles(documentSetId)
        CreateDocumentsProcessComplete(documentSetId)
      }
    }

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

  protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage

  protected trait CreateDocumentsProcessStorage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageData(fileId: Long): Iterable[(Long, Int, Option[String])]
    def writeDocuments(documents: Iterable[Document]): Unit
    def saveDocumentCount(documentSetId: Long): Unit
    def deleteTempFiles(documentSetId: Long): Unit
  }
}