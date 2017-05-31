package com.overviewdocs.searchindex

import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import play.api.libs.json.JsObject
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import com.overviewdocs.http
import com.overviewdocs.models.{Document,DocumentDisplayMethod,PdfNoteCollection}
import com.overviewdocs.query.{Field,FuzzyTermQuery,PhraseQuery,PrefixQuery}
import com.overviewdocs.util.Configuration

class ElasticSearchIndexClientSpec extends Specification {
  sequential
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  trait BaseScope extends Scope with After {
    val indexClient = new ElasticSearchIndexClient(Configuration.getString("search_index.hosts").split(","))
    await(indexClient.deleteAllIndices)

    override def after: Unit = indexClient.httpClient.shutdown

    def hostUrl(s: String): String = indexClient.hostUrl(s)

    def POST(path: String, data: String): http.Response = {
      val response = await(indexClient.httpClient.post(http.Request(
        url=hostUrl(path), maybeBody=Some(data.getBytes("utf-8"))
      )))
      assert(response.statusCode >= 200 && response.statusCode < 300)
      response
    }

    def PUT(path: String, data: String): http.Response = {
      val response = await(indexClient.httpClient.put(http.Request(
        url=hostUrl(path), maybeBody=Some(data.getBytes("utf-8"))
      )))
      assert(response.statusCode >= 200 && response.statusCode < 300)
      response
    }

    def GET(path: String): http.Response = {
      await(indexClient.httpClient.get(http.Request(url=hostUrl(path))))
    }

    def GET(path: String, data: String): http.Response = {
      await(indexClient.httpClient.get(http.Request(
        url=hostUrl(path),
        maybeBody=Some(data.getBytes("utf-8"))
      )))
    }

    def createIndex(name: String): Unit = {
      PUT(s"/$name/", """{
        "settings": {
          "index": {
            "translog_durability": "async",
            "number_of_shards": 1,
            "number_of_replicas": 0
          }
        },
        "mappings": {
          "document": {
            "properties": {
              "document_set_id": { "type": "long" }
            }
          }
        }
      }""")
    }

    def createAlias(index: String, alias: String): Unit = {
      PUT(s"/$index/_alias/$alias", "{}")
    }

    def aliasExists(index: String, alias: String): Boolean = {
      val response = GET(s"/$index/_alias/$alias")
      response.statusCode != 404 && response.body != "{}"
    }

    def idsInIndex(indexOrAlias: String): Seq[Long] = {
      import play.api.libs.json._

      val response = GET(s"/$indexOrAlias/_search", """{
        "query": { "match_all": {} },
        "fields": [ "_id" ]
      }""")
      response.statusCode must beEqualTo(200)

      val path = (JsPath \ "hits" \ "hits" \\ "_id")
      path(Json.parse(response.bodyBytes)).flatMap(_.asOpt[String]).map(_.toLong)
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
      text=s"foo$id bar baz",
      pdfNotes=PdfNoteCollection(Array()),
      thumbnailLocation=Some("path/of/file")
    )
  }

  "InMemorySearchIndex" should {
    "#addDocumentSet" should {
      "create documents_v1 if there is no documents alias" in new BaseScope {
        await(indexClient.addDocumentSet(234L))

        aliasExists("documents_v1", "documents") must beEqualTo(true)
        aliasExists("documents_v1", "documents_234") must beEqualTo(true)
      }

      "use documents_vN if it is what the documents alias points to" in new BaseScope {
        createIndex("documents_v2")
        createAlias("documents_v2", "documents")

        await(indexClient.addDocumentSet(234L))

        aliasExists("documents_v1", "documents_234") must beEqualTo(false)
        aliasExists("documents_v1", "documents") must beEqualTo(false)
        aliasExists("documents_v2", "documents_234") must beEqualTo(true)
        aliasExists("documents_v2", "documents") must beEqualTo(true) // we just created this above :)
      }

      "create an alias that filters by document set" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocumentSet(235L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L))))
        await(indexClient.addDocuments(235L, Seq(buildDocument(124L, 235L))))
        await(indexClient.refresh(234L))
        await(indexClient.refresh(235L))

        aliasExists("documents_v1", "documents_234") must beEqualTo(true)
        idsInIndex("documents_234") must beEqualTo(Seq(123L))
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
            None,
            PdfNoteCollection(Array()),
            text
          )
        }

        await(indexClient.addDocumentSet(1L))
        await(indexClient.refresh(1L))
      }

      "return an empty list when there is no document" in new HighlightScope {
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq())
      }

      "return an empty list when the term is not in the document" in new HighlightScope {
        await(indexClient.addDocuments(1L, Seq(factory.document(documentSetId=1L, id=2L, text="bar boo baz"))))
        await(indexClient.refresh(1L))
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq())
      }

      "return a highlight" in new HighlightScope {
        await(indexClient.addDocuments(1L, Seq(factory.document(documentSetId=1L, id=2L, text="boo foo bar"))))
        await(indexClient.refresh(1L))
        await(indexClient.highlight(1L, 2L, PhraseQuery(Field.All, "foo"))) must beEqualTo(Seq(Highlight(4, 7)))
      }

      "return multiple highlights" in new HighlightScope {
        await(indexClient.addDocuments(1L, Seq(factory.document(documentSetId=1L, id=2L, text="boo foo bar foo"))))
        await(indexClient.refresh(1L))
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

        await(indexClient.removeDocumentSet(234L)) // maybe throw an exception -- give nice stack trace
        aliasExists("documents_v1", "documents_234") must beEqualTo(false)
      }

      "delete associated documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocumentSet(235L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L))))
        await(indexClient.addDocuments(235L, Seq(buildDocument(124L, 235L))))
        await(indexClient.removeDocumentSet(234L))
        await(indexClient.refresh(234L))
        await(indexClient.refresh(235L))

        idsInIndex("documents") must beEqualTo(Seq(124L))
      }
    }

    "#searchForIds" should {
      "find a document" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "foo123")))

        ids must beEqualTo(Seq(123L))
      }

      "not find a document in a different document set" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocumentSet(235L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L))))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(235L, PhraseQuery(Field.All, "foo123")))

        ids must beEqualTo(Seq())
      }

      "not find a document when the query does not match" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L))))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "foo124")))

        ids must beEqualTo(Seq())
      }

      "find a term indexed from NFC and searched from NFD" in new BaseScope {
        val document = buildDocument(123L, 234L).copy(text="\u00c5oo")
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(document)))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.Text, "\u0041\u030aoo")))

        ids must containTheSameElementsAs(Seq(123L))
      }

      "find a term indexed from NFD and searched from NFKC" in new BaseScope {
        val document = buildDocument(123L, 234L).copy(text="ﬁoo")
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(document)))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.Text, "fioo")))

        ids must containTheSameElementsAs(Seq(123L))
      }

      "find a term indexed from NFD and searched from NFKC, when using default analyzer" in new BaseScope {
        val document = buildDocument(123L, 234L).copy(text="ﬁoo")
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(document)))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "fioo")))

        ids must containTheSameElementsAs(Seq(123L))
      }

      "find multiple documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "bar")))

        ids must containTheSameElementsAs(Seq(123L, 124L))
      }

      "find multiple pages of documents" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh(234L))
        val ids = await(indexClient.searchForIds(234L, PhraseQuery(Field.All, "bar"), scrollSize=1))

        ids must containTheSameElementsAs(Seq(123L, 124L))
      }

      "handle FuzzyTermQuery" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh(234L))

        val ids = await(indexClient.searchForIds(234L, FuzzyTermQuery(Field.All, "bbb", Some(2)), scrollSize=1))
        ids must containTheSameElementsAs(Seq(123L, 124L))
      }

      "handle field query" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(buildDocument(123L, 234L), buildDocument(124L, 234L))))
        await(indexClient.refresh(234L))

        val ids1 = await(indexClient.searchForIds(234L, PhraseQuery(Field.Text, "moo123"), scrollSize=1))
        ids1 must beEmpty

        val ids2 = await(indexClient.searchForIds(234L, PhraseQuery(Field.Title, "moo123"), scrollSize=1))
        ids2 must beEqualTo(Seq(123L))
      }

      "handle prefix query on title" in new BaseScope {
        await(indexClient.addDocumentSet(234L))
        await(indexClient.addDocuments(234L, Seq(
          buildDocument(123L, 234L).copy(title="foo/bar/baz.txt"),
          buildDocument(124L, 234L).copy(title="foo/baz.txt")
        )))
        await(indexClient.refresh(234L))

        val ids1 = await(indexClient.searchForIds(234L, PrefixQuery(Field.Title, "foo/bar/"), scrollSize=1))
        ids1 must beEqualTo(Seq(123L))

        val ids2 = await(indexClient.searchForIds(234L, PhraseQuery(Field.Title, "foo/"), scrollSize=1))
        ids2.length must beEqualTo(2)
      }
    }
  }
}
