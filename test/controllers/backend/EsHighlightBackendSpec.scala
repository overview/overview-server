package controllers.backend

import org.specs2.mutable.After

import com.overviewdocs.query.{Field,PhraseQuery}
import com.overviewdocs.searchindex.{Highlight,InMemoryIndexClient}

class EsHighlightBackendSpec extends NullBackendSpecification {
  trait BaseScope extends NullScope with After {
    val testIndexClient = new InMemoryIndexClient
    override def after = testIndexClient.close

    val backend = new TestNullBackend with EsHighlightBackend {
      override val indexClient = testIndexClient
    }
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      await(testIndexClient.addDocumentSet(1L))
      await(testIndexClient.refresh)
    }

    "return an empty list when there is no document" in new IndexScope {
      await(backend.index(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq())
    }

    "return an empty list when the term is not in the document" in new IndexScope {
      await(testIndexClient.addDocuments(Seq(factory.document(documentSetId=1L, id=2L, text="bar boo baz"))))
      await(testIndexClient.refresh)
      await(backend.index(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq())
    }

    "return a highlight" in new IndexScope {
      await(testIndexClient.addDocuments(Seq(factory.document(documentSetId=1L, id=2L, text="boo foo bar"))))
      await(testIndexClient.refresh)
      await(backend.index(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq(Highlight(4, 7)))
    }

    "return multiple highlights" in new IndexScope {
      await(testIndexClient.addDocuments(Seq(factory.document(documentSetId=1L, id=2L, text="boo foo bar foo"))))
      await(testIndexClient.refresh)
      await(backend.index(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq(
        Highlight(4, 7),
        Highlight(12, 15)
      ))
    }
  }
}
