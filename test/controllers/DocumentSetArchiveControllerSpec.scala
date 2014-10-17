package controllers

import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.Archive


class DocumentSetArchiveControllerSpec extends ControllerSpecification with Mockito {

  "DocumentSetArchiveController" should {

    "set content-type" in new DocumentSetArchiveContext {
      h.header(h.CONTENT_TYPE, result) must beSome(contentType)
    }
    
    "set content-length to archive size" in new DocumentSetArchiveContext {
      h.header(h.CONTENT_LENGTH, result) must beSome(s"$archiveSize")
    }
   
  }

  trait DocumentSetArchiveContext extends Scope {
    val documentSetId = 23
    val request = fakeAuthorizedRequest
    val archiveSize = 1989
    
    val contentType = "application/octet-stream"
    val controller = new DocumentSetArchiveController {
      val archiver = smartMock[Archiver]
      val archive = smartMock[Archive] 
      
      archiver.createArchive(any) returns archive
      archive.size returns archiveSize
      
      
    }
    
    lazy val result = controller.archive(documentSetId)(request)
  }
}