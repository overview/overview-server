package com.overviewdocs.searchindex

import java.util.concurrent.ExecutionException
import org.elasticsearch.common.settings.Settings
import org.elasticsearch.index.query.QueryBuilders
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.JsObject
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

import com.overviewdocs.models.{Document,DocumentDisplayMethod}
import com.overviewdocs.query.{Field,FuzzyTermQuery,PhraseQuery,PrefixQuery}

class TransportIndexClientSpec extends Specification {
  import scala.concurrent.ExecutionContext.Implicits.global
  sequential

  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  trait BaseScope extends Scope {
    val indexClient = TransportIndexClient.singleton
    val syncIndexClient = await(indexClient.connect) // implementation detail: connect just returns at a lazy val

    await(indexClient.deleteAllIndices)

    def createIndex(name: String) = {
      val settings = Settings.settingsBuilder
        .put("index.translog.durability", "async") // don't fsync
        .put("index.number_of_shards", 1)
        .put("index.number_of_replicas", 0)

      syncIndexClient.admin.indices.prepareCreate(name)
        .setSettings(settings)
        .addMapping("document", """{ "document": { "properties": { "document_set_id": { "type": "long" } } } }""")
        .execute.get
    }

    def createAlias(index: String, alias: String) = {
      syncIndexClient.admin.indices.prepareAliases
        .addAlias("documents_v2", "documents")
        .execute.get
    }

    def aliasExists(index: String, alias: String) = {
      val exists = syncIndexClient
        .admin.indices.prepareAliasesExist(alias)
        .execute.get.isExists

      if (exists) {
        val aliases = syncIndexClient
          .admin.indices.prepareGetAliases(alias)
          .execute.get.getAliases

        aliases.containsKey(index)
      } else {
        false
      }
    }

    def buildDocument(id: Long, documentSetId: Long) = Document(
      id=id,
      documentSetId=documentSetId,
      url=None,
      suppliedId="suppliedId",
      title=s"moo$id",
      pageNumber=None,
      keywords=Seq(),
      createdAt=new java.util.Date(),
      fileId=None,
      pageId=None,
      displayMethod=DocumentDisplayMethod.auto,
      isFromOcr=false,
      metadataJson=JsObject(Seq()),
      text=s"foo$id bar baz"
    )
  }

  "InMemorySearchIndex" should {
    "#addDocumentSet" should {
      "create documents_v1 if there is no documents alias" in new BaseScope {
        await(indexClient.addDocumentSet(234L))

        aliasExists("documents_v1", "documents") must beEqualTo(true)
      }

      "not create documents_v1 if there is a documents alias" in new BaseScope {
        createIndex("documents_v2")
        createAlias("documents_v2", "documents")

        await(indexClient.addDocumentSet(234L))

        aliasExists("documents_v1", "documents_234") must beEqualTo(false)
        aliasExists("documents_v1", "documents") must beEqualTo(false)
      }

      "use documents_vN if it is what the documents alias points to" in new BaseScope {
        createIndex("documents_v2")
        createAlias("documents_v2", "documents")

        await(indexClient.addDocumentSet(234L))

        aliasExists("documents_v2", "documents_234") must beEqualTo(true)
      }

      "create an alias that filters by document set" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 235L))))
        await(indexClient.refresh())

        aliasExists("documents_v1", "documents_234") must beEqualTo(true)

        val results = syncIndexClient.prepareSearch("documents_234")
          .setTypes("document")
          .setQuery(QueryBuilders.matchAllQuery)
          .setSize(2)
          .addField("id")
          .execute().get()

        val ids = results
          .getHits
          .getHits
          .map(_.id.toLong)
          .toSeq
          
        ids must beEqualTo(Seq(123L))
      }

      "be a no-op when the alias already exists" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocumentSet(234L)) must beEqualTo(())
      }
    }

    "#highlight" should {
      trait HighlightScope extends BaseScope {
        object factory {
          def document(documentSetId: Long, id: Long, text: String) = Document(
            id,
            documentSetId,
            None,
            "",
            "",
            None,
            Seq(),
            new java.util.Date(),
            None,
            None,
            DocumentDisplayMethod.auto,
            false,
            JsObject(Seq()),
            text
          )
        }

        await(indexClient.addDocumentSet(1L))
        await(indexClient.refresh)
      }

      "return an empty list when there is no document" in new HighlightScope {
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq())
      }

      "return an empty list when the term is not in the document" in new HighlightScope {
        await(indexClient.addDocuments(Seq(factory.document(documentSetId=1L, id=2L, text="bar boo baz"))))
        await(indexClient.refresh)
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq())
      }

      "return a highlight" in new HighlightScope {
        await(indexClient.addDocuments(Seq(factory.document(documentSetId=1L, id=2L, text="boo foo bar"))))
        await(indexClient.refresh)
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq(Highlight(4, 7)))
      }

      "return multiple highlights" in new HighlightScope {
        await(indexClient.addDocuments(Seq(factory.document(documentSetId=1L, id=2L, text="boo foo bar foo"))))
        await(indexClient.refresh)
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq(
          Highlight(4, 7),
          Highlight(12, 15)
        ))
      }
    }

    "#removeDocumentSet" should {
      "remove the index alias" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.removeDocumentSet(234L))

        aliasExists("documents_v1", "documents_234") must beEqualTo(false)
      }

      "succeed when removing an alias that does not exist" in new BaseScope {
        // removeDocumentSet() doesn't work when ElasticSearch is completely
        // empty. These two lines ensure there's an (empty) index.
        await(indexClient.addDocumentSet(234L))
        await(indexClient.removeDocumentSet(234L))

        // On to the test
        await(indexClient.removeDocumentSet(234L)) must not(throwA[Exception])
      }

      "delete associated documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 235L))))
        await(indexClient.refresh)
        await(indexClient.removeDocumentSet(234L))
        await(indexClient.refresh)

        val results = syncIndexClient.prepareSearch("documents")
          .setTypes("document")
          .setQuery(QueryBuilders.matchAllQuery)
          .setSize(2)
          .addField("_id")
          .execute().get()

        val ids = results
          .getHits
          .getHits
          .map(_.id.toLong)
          .toSeq
          
        ids must beEqualTo(Seq(124L))
      }
    }

    "#searchForIds" should {
      "find a document" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "foo123")))

        ids must beEqualTo(Seq(123L))
      }

      "not find a document in a different document set" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocumentSet(235L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(235L, PhraseQuery(Field.All, "foo123")))

        ids must beEqualTo(Seq())
      }

      "not find a document when the query does not match" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "foo124")))

        ids must beEqualTo(Seq())
      }

      "find a term indexed from NFC and searched from NFD" in new BaseScope {
        val document = buildDocument(123L, 234L).copy(text="\u00c5oo")
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(document)))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.Text, "\u0041\u030aoo")))

        ids must containTheSameElementsAs(Seq(123L))
      }

      "find a term indexed from NFD and searched from NFKC" in new BaseScope {
        val document = buildDocument(123L, 234L).copy(text="ﬁoo")
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(document)))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.Text, "fioo")))

        ids must containTheSameElementsAs(Seq(123L))
      }

      "find a term indexed from NFD and searched from NFKC, when using default analyzer" in new BaseScope {
        val document = buildDocument(123L, 234L).copy(text="ﬁoo")
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(document)))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "fioo")))

        ids must containTheSameElementsAs(Seq(123L))
      }

      "find multiple documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "bar")))

        ids must containTheSameElementsAs(Seq(123L, 124L))
      }

      "find multiple pages of documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "bar"), scrollSize=1))

        ids must containTheSameElementsAs(Seq(123L, 124L))
      }

      "handle FuzzyTermQuery" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())

        val ids = await(indexClient.searchForIds(234L, FuzzyTermQuery(Field.All, "bbb", Some(2)), scrollSize=1))
        ids must containTheSameElementsAs(Seq(123L, 124L))
      }

      "handle field query" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh())

        val ids1 = await(indexClient.searchForIds(234L, PhraseQuery(Field.Text, "moo123"), scrollSize=1))
        ids1 must beEmpty

        val ids2 = await(indexClient.searchForIds(234L, PhraseQuery(Field.Title, "moo123"), scrollSize=1))
        ids2 must beEqualTo(Seq(123L))
      }

      "handle prefix query on title" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(Seq(
          buildDocument(123L, 234L).copy(title="foo/bar/baz.txt"),
          buildDocument(124L, 234L).copy(title="foo/baz.txt")
        )))
        await(indexClient.refresh())

        val ids1 = await(indexClient.searchForIds(234L, PrefixQuery(Field.Title, "foo/bar/"), scrollSize=1))
        ids1 must beEqualTo(Seq(123L))

        val ids2 = await(indexClient.searchForIds(234L, PhraseQuery(Field.Title, "foo/"), scrollSize=1))
        ids2.length must beEqualTo(2)
      }
    }
  }
}
