package org.overviewproject.background.filecleanup


import java.util.concurrent.TimeoutException
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.Files

class FileRemoverSpec extends SlickSpecification with Mockito with NoTimeConversions {

  "FileRemover" should {

    "remove pages" in new FileScope {
      deleteFile 
      
      there was one(pageRemover).deleteFilePages(file.id)
    }

    "delete file content" in new FileScope {
      deleteFile
      
      there was one(blobStorage).delete(s"pglo:${file.contentsOid}")
    }

    "delete content and view if different" in new FileWithViewScope {
      deleteFile
      
      there was one(blobStorage).deleteMany(Seq(s"pglo:${file.contentsOid}", s"pglo:${file.viewOid}"))
    }

    "delete file" in new FileScope {
      deleteFile
      
      Files.filter(_.id === file.id).firstOption must beNone
    }

  }

  trait FileScope extends DbScope {
    val file = createFile

    val blobStorage = smartMock[BlobStorage]
    val pageRemover = smartMock[PageRemover]

    val blobDelete = Promise[Unit]()
    blobStorage.delete(any) returns blobDelete.future
    
    pageRemover.deleteFilePages(file.id) returns Future.successful(())

    val fileRemover = new TestFileRemover(blobStorage, pageRemover)

    protected def createFile = factory.file(referenceCount = 0)
    
    protected def deleteFile = {
      val r = fileRemover.deleteFile(file.id)
      Await.result(r, 10 millis) must throwA[TimeoutException]
      
      completeDelete
      await(r)
    }
    
    protected def completeDelete = blobDelete.success(())
  }

  trait FileWithViewScope extends FileScope {
    override def createFile = factory.file(referenceCount = 0, contentsOid = 1l, viewOid = 22l)
    val blobDeleteMany = Promise[Unit]()
    
    blobStorage.deleteMany(any) returns blobDeleteMany.future
    
    override protected def completeDelete = blobDeleteMany.success(()) 

  }

  class TestFileRemover(val blobStorage: BlobStorage, val pageRemover: PageRemover)(implicit val session: Session)
    extends FileRemover with SlickClientInSession
}