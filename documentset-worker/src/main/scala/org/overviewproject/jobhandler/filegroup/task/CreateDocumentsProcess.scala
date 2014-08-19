package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File

trait CreateDocumentsProcess {

  protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator
  
  def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean): FileGroupTaskStep =
    CreateDocumentsFromFileQueryPage(documentSetId, 0, getDocumentIdGenerator(documentSetId))

  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int, documentIdGenerator: DocumentIdGenerator) extends FileGroupTaskStep {

    override def execute: FileGroupTaskStep = {
      val files = createDocumentsProcessStorage.findFilesQueryPage(documentSetId, queryPage)

      if (files.nonEmpty) {
        val documents = files.map(createDocument(documentSetId, _))
        createDocumentsProcessStorage.writeDocuments(documents)

        CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1, documentIdGenerator)
      }
      else {
        createDocumentsProcessStorage.saveDocumentCount(documentSetId)
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

  protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage

  protected trait CreateDocumentsProcessStorage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageData(fileId: Long): Iterable[(Long, Int, Option[String])]
    def writeDocuments(documents: Iterable[Document]): Unit
    def saveDocumentCount(documentSetId: Long): Unit
  }
}