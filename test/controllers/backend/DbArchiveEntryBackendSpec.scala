package controllers.backend

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Source,Sink}
import akka.util.ByteString
import org.specs2.mock.Mockito
import play.api.libs.iteratee.{Enumerator,Iteratee}
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import models.ArchiveEntry

class DbArchiveEntryBackendSpec extends DbBackendSpecification with Mockito {
  trait BaseScope extends DbScope {
    val mockBlobStorage = smartMock[BlobStorage]
    val backend = new DbArchiveEntryBackend {
      override val blobStorage = mockBlobStorage
    }
  }

  "#showMany" should {
    "ignore documents from a different document set" in new BaseScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      val document = factory.document(documentSetId=documentSet2.id)
      await(backend.showMany(documentSet1.id, Seq(document.id))) must beEmpty
    }

    "show a text document" in new BaseScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id, title="foo.txt", text="foobar")
      await(backend.showMany(documentSet.id, Seq(document.id))) must beEqualTo(Seq(
        ArchiveEntry(document.id, "foo.txt".getBytes("utf-8"), 6)
      ))
    }

    "add a .txt extension to a document title" in new BaseScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id, title="foo.c", text="foobar")
      await(backend.showMany(documentSet.id, Seq(document.id))) must beEqualTo(Seq(
        ArchiveEntry(document.id, "foo.txt".getBytes("utf-8"), 6)
      ))
    }

    "create a .pdf from a File" in new BaseScope {
      val documentSet = factory.documentSet()
      val file = factory.file(viewSize=123L)
      val document = factory.document(documentSetId=documentSet.id, title="foo.c", text="foobar", fileId=Some(file.id))
      await(backend.showMany(documentSet.id, Seq(document.id))) must beEqualTo(Seq(
        ArchiveEntry(document.id, "foo.pdf".getBytes("utf-8"), 123)
      ))
    }

    "create a .pdf from a Page" in new BaseScope {
      val documentSet = factory.documentSet()
      val file = factory.file(viewSize=123L)
      val page = factory.page(fileId=file.id, pageNumber=2, dataSize=234L)
      val document = factory.document(documentSetId=documentSet.id, title="foo.c", text="foobar", fileId=Some(file.id), pageNumber=Some(2), pageId=Some(page.id))
      await(backend.showMany(documentSet.id, Seq(document.id))) must beEqualTo(Seq(
        ArchiveEntry(document.id, "foo-p2.pdf".getBytes("utf-8"), 234)
      ))
    }
  }

  "#streamBytes" should {
    trait StreamBytesScope extends BaseScope {
      def mockBytes(location: String, s: String): Unit = {
        mockBlobStorage.get(location) returns Source.single(ByteString(s.getBytes("utf-8")))
      }

      def consume(documentSetId: Long, documentId: Long): String = {
        implicit val system = ActorSystem()
        implicit val mat = ActorMaterializer.create(system)
        import system.dispatcher

        val source = backend.streamBytes(documentSetId, documentId)
        val sink = Sink.fold[ByteString, ByteString](ByteString())(_ ++ _)
        val bytes: Array[Byte] = await(source.runWith(sink).map(_.toArray))

        system.terminate
        new String(bytes, "utf-8")
      }
    }

    "return an empty Array for documents from a different document set" in new StreamBytesScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      val document = factory.document(documentSetId=documentSet2.id, text="foobar")
      consume(documentSet1.id, document.id) must beEqualTo("")
    }

    "return page contents" in new StreamBytesScope {
      val documentSet = factory.documentSet()
      val file = factory.file()
      val page = factory.page(fileId=file.id, dataLocation="page:a:b")
      val document = factory.document(documentSetId=documentSet.id, fileId=Some(file.id), pageId=Some(page.id), text="foo")
      mockBytes("page:a:b", "bar")
      consume(documentSet.id, document.id) must beEqualTo("bar")
    }

    "return file contents" in new StreamBytesScope {
      val documentSet = factory.documentSet()
      val file = factory.file(viewLocation="file:a:b")
      val document = factory.document(documentSetId=documentSet.id, fileId=Some(file.id), text="foo")
      mockBytes("file:a:b", "bar")
      consume(documentSet.id, document.id) must beEqualTo("bar")
    }

    "return document text" in new StreamBytesScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id, text="foo")
      consume(documentSet.id, document.id) must beEqualTo("foo")
    }
  }
}
