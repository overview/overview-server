package org.overviewproject.util

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.collection.mutable.Buffer
import scala.concurrent.Future

import org.overviewproject.models.Document

class BulkDocumentWriterSpec extends Specification {
  private def await[A](f: Future[A]): A = scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)

  trait BaseScope extends Scope {
    val flushes: Buffer[Iterable[Document]] = Buffer()
    val subject = new BulkDocumentWriter {
      override val maxNDocuments = 3
      override val maxNBytes = 1000

      override def flushImpl(documents: Iterable[Document]) = {
        flushes.append(documents)
        Future.successful(())
      }
    }

    object factory {
      def document(text: String) = Document(
        1L,
        1L,
        Some("http://18-byte-url"),
        "18-char-suppliedId",
        "13-char-title",
        None,
        Seq(),
        new java.util.Date(),
        None,
        None,
        text
      )
    }

    def add(document: Document): Document = {
      await(subject.addAndFlushIfNeeded(document))
      document
    }

    // Convenience method. Adds and returns a Document with text
    def addText(text: String): Document = add(factory.document(text))
  }

  "should not flush when empty" in new BaseScope {
    await(subject.flush)
    flushes must beEmpty
  }

  "should flush when non-empty" in new BaseScope {
    val doc1 = addText("foobar")
    flushes.length must beEqualTo(0)
    await(subject.flush)
    flushes.length must beEqualTo(1)
    flushes(0) must containTheSameElementsAs(Seq(doc1))
  }

  "should empty when flushing" in new BaseScope {
    addText("foobar")
    await(subject.flush)
    await(subject.flush)
    flushes.length must beEqualTo(1)
  }

  "flush on add when adding more than N documents" in new BaseScope {
    addText("doc1")
    addText("doc2")
    flushes.length must beEqualTo(0)
    addText("doc3")
    flushes.length must beEqualTo(1)
  }

  "flush on add when adding more than N bytes" in new BaseScope {
    addText("doc1")
    flushes.length must beEqualTo(0)
    addText("""
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
      mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm
    """)
    flushes.length must beEqualTo(1)
  }
}
