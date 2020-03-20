package controllers

import akka.stream.scaladsl.{Source,Sink}
import akka.util.ByteString
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.BlobStorageRef
import com.overviewdocs.test.factories.{PodoFactory=>factory}
import controllers.backend.{DocumentSetFileBackend,File2Backend}

class DocumentSetFileControllerSpec extends ControllerSpecification with Mockito {
  trait BaseScope extends Scope {
    val mockDocumentSetFileBackend = mock[DocumentSetFileBackend]
    val mockFile2Backend = smartMock[File2Backend]
    val mockBlobStorage = smartMock[BlobStorage]
    val controller = new DocumentSetFileController(mockDocumentSetFileBackend, mockFile2Backend, mockBlobStorage, fakeControllerComponents)
  }

  "#head" should {
    trait HeadScope extends BaseScope {
      val documentSetId = 123L
      val sha1 = Array(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19).map(_.toByte)
      lazy val result = controller.head(documentSetId, sha1)(fakeAuthorizedRequest)
    }

    "return 204 No Content when a match exists" in new HeadScope {
      mockDocumentSetFileBackend.existsByIdAndSha1(documentSetId, sha1) returns Future.successful(true)
      h.status(result) must beEqualTo(h.NO_CONTENT)
    }

    "return 404 Not Found when no match exists" in new HeadScope {
      mockDocumentSetFileBackend.existsByIdAndSha1(documentSetId, sha1) returns Future.successful(false)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val documentSetId = 123L
      val file2 = factory.file2(filename="a file.doc", contentType="application/content-type", blob=Some(BlobStorageRef("s3:hi:there", 8)))
      lazy val result = controller.show(documentSetId, file2.id)(fakeAuthorizedRequest)

      def mockBytes(location: String, s: String): Unit = {
        mockBlobStorage.get(location) returns Source.single(ByteString(s.getBytes("utf-8")))
      }
    }

    "return 404 Not Found when DocumentSetFile2 does not exist" in new ShowScope {
      mockDocumentSetFileBackend.exists(documentSetId, file2.id) returns Future.successful(false)
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return data" in new ShowScope {
      mockDocumentSetFileBackend.exists(documentSetId, file2.id) returns Future.successful(true)
      mockFile2Backend.lookup(file2.id) returns Future.successful(Some(file2))
      mockBytes("s3:hi:there", "hi there")
      h.contentType(result) must beSome("application/content-type")
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename*=UTF-8''a%20file.doc")
      h.contentAsString(result) must beEqualTo("hi there")
    }
  }
}
