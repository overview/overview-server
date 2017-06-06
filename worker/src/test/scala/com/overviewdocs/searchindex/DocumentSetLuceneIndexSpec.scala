package com.overviewdocs.searchindex

import org.apache.lucene.store.RAMDirectory
import org.apache.lucene.index.{IndexWriter,IndexWriterConfig}
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod,PdfNoteCollection}
import com.overviewdocs.query.{Field,FuzzyTermQuery,Query,PhraseQuery,PrefixQuery}

class DocumentSetLuceneIndexSpec extends Specification {
  sequential

  trait BaseScope extends Scope {
    val directory = new RAMDirectory

    def openIndex = new DocumentSetLuceneIndex(directory)
    lazy val index = openIndex

    def buildDocument(id: Long) = Document(
      id=id,
      documentSetId=1L,
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

    def search(q: Query): String = index.searchForIds(q).mkString(",")
  }

  "DocumentSetLuceneIndex" should {
    "#searchForIds" should {
      "find a document" in new BaseScope {
        index.addDocuments(Seq(buildDocument(12345L), buildDocument(23456L)))
        search(PhraseQuery(Field.All, "foo12345")) must beEqualTo("12345")
      }

      "find zero documents when the query does not match" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L)))
        search(PhraseQuery(Field.All, "foo2")) must beEqualTo("")
      }

      "find a term indexed from NFC and searched from NFD" in new BaseScope {
        val document = buildDocument(1L).copy(text="\u00c5oo")
        index.addDocuments(Seq(document))
        search(PhraseQuery(Field.Text, "\u0041\u030aoo")) must beEqualTo("1")
      }

      "find a term indexed from NFD and searched from NFKC" in new BaseScope {
        val document = buildDocument(1L).copy(text="ﬁoo")
        index.addDocuments(Seq(document))
        search(PhraseQuery(Field.Text, "fioo")) must beEqualTo("1")
      }

      "find a term indexed from NFD and searched from NFKC, when using _all" in new BaseScope {
        val document = buildDocument(1L).copy(text="ﬁoo")
        index.addDocuments(Seq(document))
        search(PhraseQuery(Field.All, "fioo")) must beEqualTo("1")
      }

      "find multiple documents" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L), buildDocument(2L), buildDocument(3L)))
        search(PhraseQuery(Field.All, "bar")) must beEqualTo("1,2,3")
      }

      "handle FuzzyTermQuery" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L), buildDocument(2L), buildDocument(99L)))
        search(FuzzyTermQuery(Field.All, "foo", Some(1))) must beEqualTo("1,2")
      }

      "handle field query" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L), buildDocument(2L)))
        search(PhraseQuery(Field.Text, "moo1")) must beEqualTo("")
        search(PhraseQuery(Field.Title, "moo1")) must beEqualTo("1")
      }

      "handle prefix query" in new BaseScope {
        index.addDocuments(Seq(
          buildDocument(1L).copy(text="The cow hopped over the moon"),
          buildDocument(2L).copy(text="The cow hoped he would survive the night"),
          buildDocument(3L).copy(text="The cow lay on the grass")
        ))

        search(PrefixQuery(Field.Text, "cow hop")) must beEqualTo("1,2")
        search(PrefixQuery(Field.Text, "cow hopp")) must beEqualTo("1")
      }

      "handle prefix query on title" in new BaseScope {
        index.addDocuments(Seq(
          buildDocument(1L).copy(title="foo/bar/baz.txt"),
          buildDocument(2L).copy(title="foo/baz.txt")
        ))

        search(PrefixQuery(Field.Title, "foo/bar/")) must beEqualTo("1")
        search(PrefixQuery(Field.Title, "foo/")) must beEqualTo("1,2")
      }
    }

    "#highlight" should {
      "return empty result on document it cannot match" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L).copy(text="i am cow hear me moo")))
        index.highlight(1L, PhraseQuery(Field.Text, "dog")) must beEmpty
      }

      "match a word" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L).copy(text="i am cow hear me moo")))
        index.highlight(1L, PhraseQuery(Field.Text, "cow")) must containTheSameElementsAs(Seq(Utf16Highlight(5, 8)))
      }

      "match overlapping words" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L).copy(text="this is a hard, hard, hard query")))
        index.highlight(1L, PhraseQuery(Field.Text, "hard, hard")) must containTheSameElementsAs(Seq(Utf16Highlight(10,14), Utf16Highlight(16,20), Utf16Highlight(22,26)))
      }

      "return utf-16 indexes" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L).copy(text="\ud86d\udfa9 foo")))
        index.highlight(1L, PhraseQuery(Field.Text, "foo")) must containTheSameElementsAs(Seq(Utf16Highlight(3, 6)))
      }
    }

    "#highlights" should {
      "return empty result on document it cannot match" in new BaseScope {
        index.addDocuments(Seq(buildDocument(1L).copy(text="i am cow hear me moo")))
        index.highlights(Seq(1L), PhraseQuery(Field.Text, "cow")) must beEqualTo(Map(
          1L -> Seq(Utf16Snippet(0, 20, Vector(Utf16Highlight(5, 8))))
        ))
      }

      "map documents to each other correctly" in new BaseScope {
        index.addDocuments(Seq(
          buildDocument(1L).copy(text="i am cow hear me moo"),
          buildDocument(2L).copy(text="i am cow hear me moo moo moo")
        ))
        index.highlights(Seq(1L, 2L), PhraseQuery(Field.Text, "cow")) must beEqualTo(Map(
          1L -> Seq(Utf16Snippet(0, 20, Vector(Utf16Highlight(5, 8)))),
          2L -> Seq(Utf16Snippet(0, 28, Vector(Utf16Highlight(5, 8))))
        ))
      }

      "omit documents with no matches" in new BaseScope {
        index.addDocuments(Seq(
          buildDocument(1L).copy(text="i am cow hear me moo"),
          buildDocument(2L).copy(text="i am dog hear me woof")
        ))
        index.highlights(Seq(1L, 2L), PhraseQuery(Field.Text, "cow")) must beEqualTo(Map(
          1L -> Seq(Utf16Snippet(0, 20, Vector(Utf16Highlight(5, 8))))
        ))
      }

      "highlight prefixed matches" in new BaseScope {
        // A prefix query is "rewritten" for Lucene so that its terms actually
        // come from the index. Highlight index terms, not query terms.
        index.addDocuments(Seq(
          buildDocument(1L).copy(text="i am cow hear me moo")
        ))
        index.highlights(Seq(1L), PrefixQuery(Field.Text, "co")) must beEqualTo(Map(
          1L -> Seq(Utf16Snippet(0, 20, Vector(Utf16Highlight(5, 8))))
        ))
      }

      "highlight '_all' prefixed matches as 'text'" in new BaseScope {
        // A prefix query is "rewritten" for Lucene so that its terms actually
        // come from the index. Highlight index terms, not query terms.
        index.addDocuments(Seq(
          buildDocument(1L).copy(text="i am cow hear me moo")
        ))
        index.highlights(Seq(1L), PrefixQuery(Field.All, "co")) must beEqualTo(Map(
          1L -> Seq(Utf16Snippet(0, 20, Vector(Utf16Highlight(5, 8))))
        ))
      }
    }
  }
}
