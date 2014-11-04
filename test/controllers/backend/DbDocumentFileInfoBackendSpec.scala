package controllers.backend

import models.FileViewInfo
import models.PageViewInfo


class DbDocumentFileInfoBackendSpec extends DbBackendSpecification {

  "DbDocumentFileInfoBackend" should {
    
    "find info for pages" in new SplitDocumentsScope {
      val infos = await(backend.indexDocumentFileInfosForPages(documentSet.id))
      
      infos must containTheSameElementsAs(pageInfos)
    }
    
    "find info for files" in new  FileScope {
      val infos = await(backend.indexDocumentFileInfosForFiles(documentSet.id))
      
      infos must containTheSameElementsAs(fileInfos)
    }
  }
  
  trait SplitDocumentsScope extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentFileInfoBackend
    
    val filename = "filename"
    val numberOfPages = 2
    val documentSet = factory.documentSet()
    val file = factory.file(name = filename)
    val pages = Seq.tabulate(numberOfPages)(n => factory.page(pageNumber = (n + 1), fileId = file.id))
    val documents = pages.map(p => 
      factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id), 
          pageId = Some(p.id), pageNumber = Some(p.pageNumber)))
      
    val pageInfos = pages.map(p => PageViewInfo(filename, p.pageNumber, p.id, p.data.get.length))
  }
  
  trait FileScope extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentFileInfoBackend
    
    val filename = "filename"
    val oid = 1234l
    val size = 456l
    
    val documentSet = factory.documentSet()
    val file = factory.file(name = filename, viewOid = oid, viewSize = Some(size))
    val document = factory.document(documentSetId = documentSet.id, title = filename, fileId = Some(file.id))
    
    val fileInfos = Seq(FileViewInfo(filename, oid, size))
  }
}