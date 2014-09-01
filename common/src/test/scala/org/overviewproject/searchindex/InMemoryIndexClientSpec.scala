package org.overviewproject.searchindex

import java.util.concurrent.ExecutionException
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.indices.IndexMissingException
import org.specs2.mutable.{After,Specification}
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

import org.overviewproject.tree.orm.Document // FIXME should be model

class InMemoryIndexClientSpec extends Specification {
  import scala.concurrent.ExecutionContext.Implicits.global
  sequential

  trait BaseScope extends After {
    lazy val indexClient = new InMemoryIndexClient()

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
    "#addDocumentSet" should {
      "create an alias that filters by document set" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 235L))))
        await(indexClient.refresh())

        val resultsFuture = indexClient.publicClientFuture.map { client =>
          client.prepareSearch("documents_234")
            .setTypes("document")
            .setQuery(QueryBuilders.queryString("*:*"))
            .setSize(2)
            .addField("id")
            .execute().get()
        }

        val ids = await(resultsFuture)
          .getHits
          .getHits
          .map(_.field("id").value[Object].toString.toLong)
          .toSeq
          
        ids must beEqualTo(Seq(123L))
      }
    }

    "#removeDocumentSet" should {
      "remove the index alias" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.removeDocumentSet(234L))

        val future = indexClient.publicClientFuture.map { client =>
          client.prepareSearch("documents_234")
            .setTypes("document")
            .setQuery(QueryBuilders.queryString("*:*"))
            .setSize(2)
            .addField("id")
            .execute().get()
        }

        await(future) must throwA[ExecutionException].like { case t: Throwable =>
          t.getCause must beAnInstanceOf[IndexMissingException]
        }
      }

      "delete associated documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 235L))))
        await(indexClient.refresh)
        await(indexClient.removeDocumentSet(234L))

        val resultsFuture = indexClient.publicClientFuture.map { client =>
          client.prepareSearch("documents")
            .setTypes("document")
            .setQuery(QueryBuilders.queryString("*:*"))
            .setSize(2)
            .addField("id")
            .execute().get()
        }

        val ids = await(resultsFuture)
          .getHits
          .getHits
          .map(_.field("id").value[Object].toString.toLong)
          .toSeq
          
        ids must beEqualTo(Seq(124L))
      }
    }

    "#searchForIds" should {
      "find a document" in new BaseScope {
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, "foo123"))

        ids must beEqualTo(Seq(123L))
      }

      "not find a document in a different document set" in new BaseScope {
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(235L, "foo123"))

        ids must beEqualTo(Seq())
      }

      "not find a document when the query does not match" in new BaseScope {
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, "foo124"))

        ids must beEqualTo(Seq())
      }

      "find multiple documents" in new BaseScope {
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, "bar"))

        ids must containTheSameElementsAs(Seq(123L, 124L))
      }
    }
  }
}
