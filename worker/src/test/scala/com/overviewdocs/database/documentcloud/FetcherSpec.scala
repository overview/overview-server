package com.overviewdocs.documentcloud

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.models.Document
import com.overviewdocs.util.AwaitMethod

class FetcherSpec extends Specification with Mockito with AwaitMethod {
  import HeaderProducer._

  trait BaseScope extends Scope {
    val server = smartMock[DocumentCloudServer]
    val producer = smartMock[HeaderProducer]
    producer.username returns "username"
    producer.password returns "password"
    val writer = smartMock[DocumentWriter]

    val subject = new Fetcher(server)
    def run = await(subject.run(producer, writer)) must beEqualTo(())
    def F[A](a: A): Future[A] = Future.successful(a)
  }

  "Fetcher" should {
    "work with an empty producer" in new BaseScope {
      producer.next returns F(End)
      run
      there was no(writer).addDocument(any)
    }

    "add a Document" in new BaseScope {
      val header = DocumentCloudDocumentHeader(1L, 2L, "3", "title", Some(4), "http://text.url", "public")
      producer.next returns F(Fetch(header)) thenReturns F(End)
      server.getText("http://text.url", "username", "password", "public") returns F(Right("text"))

      run

      val captor = capture[Document]
      there was one(writer).addDocument(captor)
      val document = captor.value
      document.id must beEqualTo(1L)
      document.documentSetId must beEqualTo(2L)
      document.url must beSome("https://www.documentcloud.org/documents/3#p4")
      document.suppliedId must beEqualTo("3")
      document.title must beEqualTo("title")
      document.pageNumber must beSome(4)
      document.text must beEqualTo("text")
    }

    "add an error" in new BaseScope {
      val header = DocumentCloudDocumentHeader(1L, 2L, "3", "title", Some(4), "http://text.url", "public")
      producer.next returns F(Fetch(header)) thenReturns F(End)
      server.getText("http://text.url", "username", "password", "public") returns F(Left("text"))

      run

      there was no(writer).addDocument(any)
      val captors = (capture[String], capture[String])
      there was one(writer).addError(captors._1, captors._2)
      captors._1.value must beEqualTo("3")
      captors._2.value must beEqualTo("text")
    }

    "skip" in new BaseScope {
      producer.next returns F(Skip(3)) thenReturns F(End)
      run
      there was one(writer).skip(3)
    }

    "loop" in new BaseScope {
      // We'll loop Skip()s because that's briefer than Fetch()es.
      producer.next returns F(Skip(1)) thenReturns F(Skip(2)) thenReturns F(End)
      run
      there was one(writer).skip(1)
      there was one(writer).skip(2)
    }
  }
}
