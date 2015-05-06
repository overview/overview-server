package org.overviewproject.jobhandler.filegroup.task.step

import java.io.{ByteArrayInputStream,InputStream}
import org.mockito.Matchers
import org.specs2.mock.Mockito
import scala.concurrent.Future
import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.File
import org.overviewproject.models.tables.Files
import org.overviewproject.models.tables.TempDocumentSetFiles
import org.overviewproject.test.{DbSpecification,SlickClientInSession}

class CreatePdfFileSpec extends DbSpecification with Mockito {

  "CreatePdfFile" should {

    "copy upload contents to BlobStorage" in new UploadScope {
      await(createFile.execute)

      there was one(blobStorage).create(
        Matchers.eq(BlobBucketId.FileContents),
        any[InputStream],
        Matchers.eq(uploadSize)
      )
    }

    "create a file with PDF content" in new UploadScope {
      await(createFile.execute)

      import org.overviewproject.database.Slick.simple._
      val file = Files.firstOption(session)

      file.map(f => (f.contentsLocation, f.contentsSize, f.viewLocation, f.viewSize)) must
        beSome((location, uploadSize, location, uploadSize))

      // We can't check the sha1, because our upload mock doesn't *do* anything
      // with the input stream. (Our impementation uses a DigestInputStream.)
      // But we *can* check that *a* sha1 gets passed.
      file.flatMap(_.contentsSha1) must beSome[Array[Byte]].which(_.length  == 20)
    }

    "return the next step" in new UploadScope {
      val r = await(createFile.execute)

      r must be equalTo(NextStep)
    }

    "return failure on error" in new FailingCreationScope {
      createFile.execute must throwA[Exception].await
    }

    "write a tempDocumentSetFile" in new UploadScope {
      await(createFile.execute)

      import org.overviewproject.database.Slick.simple._
      TempDocumentSetFiles.firstOption(session) must beSome
    }

    trait UploadScope extends DbScope {
      val fileGroup = factory.fileGroup()
      val uploadSize = 1000l
      val upload = factory.groupedFileUpload(fileGroupId = fileGroup.id, size = uploadSize)
      val location = "blob location"

      val loInputStream = new ByteArrayInputStream("foo bar baz".getBytes("UTF-8"))
      val blobStorage = smartMock[BlobStorage]
      blobStorage.create(any, any, any) returns createResult

      val createFile = new TestCreatePdfFile(upload.id, blobStorage, loInputStream)

      def createResult: Future[String] = Future.successful(location)
    }

    trait FailingCreationScope extends UploadScope {
      override def createResult = Future.failed(new Exception)
    }
  }

  case object NextStep extends TaskStep {
    override def execute = Future.successful(this)
  }

  class TestCreatePdfFile(val uploadedFileId: Long, val blobStorage: BlobStorage, loInputStream: InputStream)
  (implicit val session: Session) extends CreatePdfFile with SlickClientInSession {
    override protected val documentSetId = 1l
    override protected def nextStep(file: File) = NextStep
    override protected def largeObjectInputStream(oid: Long) = loInputStream
  }
}
