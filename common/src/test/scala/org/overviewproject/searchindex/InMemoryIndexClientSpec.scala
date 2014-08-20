package org.overviewproject.searchindex

import org.specs2.mutable.{After,Specification}
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

import org.overviewproject.tree.orm.Document // FIXME should be model

class InMemoryIndexClientSpec extends Specification {
  import scala.concurrent.ExecutionContext.Implicits.global
  sequential

  trait BaseScope extends After {
    lazy val indexClient = InMemoryIndexClient()

    private val awaitDuration = Duration(2, "s")
    def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

    def buildDocument(id: Long, documentSetId: Long) = Document(
      id=id,
      documentSetId=documentSetId,
      description=s"description$id",
      title=Some(s"moo$id"),
      text=Some(s"foo$id bar baz")
    )

    override def after = indexClient.close
  }

  "InMemorySearchIndex" should {
    "find a document" in new BaseScope {
      val idsFuture: Future[Seq[Long]] = indexClient.addDocumentSet(234L)
        .flatMap(Unit => indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        .flatMap(Unit => indexClient.refresh())
        .flatMap(Unit => indexClient.searchForIds(234L, "foo123"))

      await(idsFuture) must beEqualTo(Seq(123L))
    }

    "not find a document in a different document set" in new BaseScope {
      val idsFuture: Future[Seq[Long]] = indexClient.addDocumentSet(234L)
        .flatMap(Unit => indexClient.addDocumentSet(235L))
        .flatMap(Unit => indexClient.addDocuments(Seq(buildDocument(123L, 234L))))
        .flatMap(Unit => indexClient.refresh())
        .flatMap(Unit => indexClient.searchForIds(235L, "foo123"))

      await(idsFuture) must beEqualTo(Seq())
    }

    "not find a document when the query does not match" in new BaseScope {
      val idsFuture: Future[Seq[Long]] = indexClient.addDocumentSet(234L)
        .flatMap(Unit => indexClient.addDocumentSet(234L))
        .flatMap(Unit => indexClient.addDocuments(Seq(buildDocument(123L, 234L))))
        .flatMap(Unit => indexClient.refresh())
        .flatMap(Unit => indexClient.searchForIds(234L, "foo124"))

      await(idsFuture) must beEqualTo(Seq())
    }

    "find multiple documents" in new BaseScope {
      val idsFuture: Future[Seq[Long]] = indexClient.addDocumentSet(234L)
        .flatMap(Unit => indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        .flatMap(Unit => indexClient.refresh())
        .flatMap(Unit => indexClient.searchForIds(234L, "bar"))

      await(idsFuture) must containTheSameElementsAs(Seq(123L, 124L))
    }
  }
}
