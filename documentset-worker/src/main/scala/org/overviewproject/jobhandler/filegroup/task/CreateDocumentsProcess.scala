package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.File

trait CreateDocumentsProcess {

  def startCreateDocumentsTask(documentSetId: Long): FileGroupTaskStep =
    CreateDocumentsFromFileQueryPage(documentSetId, 0)
    
  private case class CreateDocumentsFromFileQueryPage(documentSetId: Long, queryPage: Int) extends FileGroupTaskStep {
    private var documentIndex = 0
    
    override def execute: FileGroupTaskStep = {
      val files = storage.findFilesQueryPage(documentSetId, queryPage)
      val documents = files.map(createDocument(documentSetId, _))

      storage.writeDocuments(documents)
      
      CreateDocumentsFromFileQueryPage(documentSetId, queryPage + 1)
    } 

    private def createDocument(documentSetId: Long, file: File) = {
      val pages = storage.findFilePageData(file.id)
      
      val text = pages.foldLeft("")((text, page) => text + page._3.getOrElse(""))
      
      documentIndex += 1
      Document(documentSetId, 
          id = (documentSetId << 32) | documentIndex,
          title = Some(file.name),
          text = Some(text),
          fileId = Some(file.id))
    }
  }
  
  protected val storage: Storage
  
  protected trait Storage {
    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File]
    def findFilePageData(fileId: Long): Iterable[(Long, Int, Option[String])]
    def writeDocuments(documents: Iterable[Document]): Int
  }
}