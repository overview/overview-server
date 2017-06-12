package controllers.backend

import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.query.{Field,PhraseQuery,Query}
import com.overviewdocs.models.DocumentIdSet
import com.overviewdocs.searchindex.{SearchResult,SearchWarning}
import models.{InMemorySelection,SelectionRequest,SelectionWarning}

class DbDocumentSelectionBackendSpec extends DbBackendSpecification with Mockito {
  "DbDocumentSelectionBackend" should {
    "#createSelection" should {
      trait CreateSelectionScope extends DbScope {
        val documentSet = factory.documentSet(1L)
        val doc1 = factory.document(documentSetId=1L, id=(1L << 32) | 0L, title="c", text="foo bar baz oneandtwo oneandthree")
        val doc2 = factory.document(documentSetId=1L, id=(1L << 32) | 1L, title="a", text="moo mar maz oneandtwo twoandthree")
        val doc3 = factory.document(documentSetId=1L, id=(1L << 32) | 2L, title="b", text="noo nar naz oneandthree twoandthree")
        val documents = Seq(doc1, doc2, doc3)

        val documentIds: Seq[Long] = Seq()
        val tagIds: Seq[Long] = Seq()
        val nodeIds: Seq[Long] = Seq()
        val storeObjectIds: Seq[Long] = Seq()
        val tagged: Option[Boolean] = None
        val tagOperation: SelectionRequest.TagOperation = SelectionRequest.TagOperation.Any
        val q: Option[Query] = None

        def request = SelectionRequest(
          documentSetId=documentSet.id,
          documentIds=documentIds,
          tagIds=tagIds,
          nodeIds=nodeIds,
          storeObjectIds=storeObjectIds,
          tagged=tagged,
          q=q,
          tagOperation=tagOperation
        )

        val searchBackend = smartMock[SearchBackend]
        def searchDocumentIds: DocumentIdSet = DocumentIdSet.empty
        def searchWarnings: List[SearchWarning] = Nil
        def searchResult: SearchResult = SearchResult(searchDocumentIds, searchWarnings)
        searchBackend.search(any, any) returns Future { searchResult }

        private val sortedIds = s"{${doc2.id},${doc3.id},${doc1.id}}"

        import database.api._
        blockingDatabase.runUnit(sqlu"""
          UPDATE document_set
          SET sorted_document_ids = $sortedIds::BIGINT[]
          WHERE id = ${documentSet.id}
        """)

        val backend = new DbDocumentSelectionBackend(searchBackend)
        lazy val ret: InMemorySelection = await(backend.createSelection(request))
      }

      "show all documents by default" in new CreateSelectionScope {
        ret.documentIds.size must beEqualTo(3)
      }

      "sort documents by title" in new CreateSelectionScope {
        ret.documentIds must beEqualTo(Array(doc2.id, doc3.id, doc1.id))
      }

      "search by q" in new CreateSelectionScope {
        override val q = Some(PhraseQuery(Field.All, "moo"))
        override def searchDocumentIds = DocumentIdSet(Seq(doc2.id))
        ret.documentIds must beEqualTo(Array(doc2.id))
        there was one(searchBackend).search(documentSet.id, q.get)
      }

      "pass through warnings in q" in new CreateSelectionScope {
        override val q = Some(PhraseQuery(Field.All, "moo"))
        override def searchWarnings = List(
          SearchWarning.TooManyExpansions(Field.Text, "foo", 2),
          SearchWarning.TooManyExpansions(Field.Title, "bar", 2)
        )
        ret.warnings must beEqualTo(searchWarnings.map(w => SelectionWarning.SearchIndexWarning(w)))
      }

      trait IndexIdsWithTagsScope extends CreateSelectionScope {
        // tag1: doc1, doc2
        // tag2: doc2
        //
        // Put otherwise:
        //
        // doc1: tag1
        // doc2: tag1, tag2
        val tag1 = factory.tag(documentSetId=documentSet.id)
        val tag2 = factory.tag(documentSetId=documentSet.id)

        factory.documentTag(doc1.id, tag1.id)
        factory.documentTag(doc2.id, tag1.id)
        factory.documentTag(doc2.id, tag2.id)
      }

      "search by tagId" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag1.id)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Array(doc2.id, doc1.id))
      }

      "search by ANY tagIds" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag1.id, tag2.id)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Array(doc2.id, doc1.id))
      }

      "search by ALL tagIds" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag1.id, tag2.id)
        override val tagOperation = SelectionRequest.TagOperation.All
        ret.documentIds must beEqualTo(Array(doc2.id))
      }

      "search by NONE tagIds" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag1.id, tag2.id)
        override val tagOperation = SelectionRequest.TagOperation.None
        ret.documentIds must beEqualTo(Array(doc3.id))
      }

      "search by tagged=false" in new IndexIdsWithTagsScope {
        override val tagged = Some(false)
        ret.documentIds must beEqualTo(Array(doc3.id))
      }

      "search by tagged=true" in new IndexIdsWithTagsScope {
        override val tagged = Some(true)
        ret.documentIds must beEqualTo(Array(doc2.id, doc1.id))
      }

      "search by tagged=false OR a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag2.id)
        override val tagged = Some(false)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Array(doc2.id, doc3.id))
      }

      "search by tagged=false AND a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag2.id)
        override val tagged = Some(false)
        override val tagOperation = SelectionRequest.TagOperation.All
        ret.documentIds must beEqualTo(Array.empty)
      }

      "search by NOT tagged=false AND NOT a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag2.id)
        override val tagged = Some(false)
        override val tagOperation = SelectionRequest.TagOperation.None
        ret.documentIds must beEqualTo(Array(doc1.id))
      }

      "search by tagged=true OR a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag2.id)
        override val tagged = Some(true)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Array(doc2.id, doc1.id))
      }

      "search by tagged=true AND a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag2.id)
        override val tagged = Some(true)
        override val tagOperation = SelectionRequest.TagOperation.All
        ret.documentIds must beEqualTo(Array(doc2.id))
      }

      "search by NOT tagged=true AND NOT a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Seq(tag2.id)
        override val tagged = Some(true)
        override val tagOperation = SelectionRequest.TagOperation.None
        ret.documentIds must beEqualTo(Array(doc3.id))
      }

      "intersect results from search index and database" in new CreateSelectionScope {
        val tag = factory.tag(documentSetId=documentSet.id)
        factory.documentTag(doc1.id, tag.id)
        factory.documentTag(doc2.id, tag.id)

        override val tagIds = Seq(tag.id)
        override val q = Some(PhraseQuery(Field.All, "oneandthree"))
        override def searchDocumentIds = DocumentIdSet(Seq(doc1.id, doc3.id))

        ret.documentIds must beEqualTo(Array(doc1.id))
      }

      "search by nodeIds" in new CreateSelectionScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        override val nodeIds = Seq(node.id)
        ret.documentIds must beEqualTo(Array(doc2.id, doc1.id))
      }

      "search by documentIds" in new CreateSelectionScope {
        override val documentIds = Seq(doc1.id)
        ret.documentIds must beEqualTo(Array(doc1.id))
      }

      "intersect two Postgres filters" in new CreateSelectionScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        val tag = factory.tag(documentSetId=documentSet.id)
        factory.documentTag(doc1.id, tag.id)
        factory.documentTag(doc3.id, tag.id)

        override val nodeIds = Seq(node.id)
        override val tagIds = Seq(tag.id)

        ret.documentIds must beEqualTo(Array(doc1.id))
      }

      "search by storeObjectIds" in new CreateSelectionScope {
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        val store = factory.store(apiToken=apiToken.token)
        val obj = factory.storeObject(storeId=store.id)
        val dvo1 = factory.documentStoreObject(doc1.id, obj.id)
        val dvo2 = factory.documentStoreObject(doc2.id, obj.id)

        override val storeObjectIds = Seq(obj.id)

        ret.documentIds must beEqualTo(Array(doc2.id, doc1.id))
      }
    }
  }
}
