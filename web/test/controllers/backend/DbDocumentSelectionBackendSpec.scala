package controllers.backend

import akka.stream.scaladsl.Source
import com.google.re2j.Pattern
import org.specs2.mock.Mockito
import play.api.Configuration
import play.api.libs.json.Json
import scala.collection.{immutable,mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise}

import com.overviewdocs.query.{AndQuery,Field,NotQuery,OrQuery,PhraseQuery,PrefixQuery,Query,RegexQuery}
import com.overviewdocs.messages.Progress
import com.overviewdocs.metadata.{MetadataField,MetadataFieldDisplay,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.{DocumentIdList,DocumentIdSet,ViewFilter}
import com.overviewdocs.searchindex.{SearchResult,SearchWarning}
import models.{InMemorySelection,SelectionRequest,SelectionWarning,ViewFilterSelection}
import test.helpers.InAppSpecification

class DbDocumentSelectionBackendSpec extends DbBackendSpecification with InAppSpecification with Mockito {
  "DbDocumentSelectionBackend" should {
    "#createSelection" should {
      trait CreateSelectionScope extends DbBackendScope {
        val documentSet = factory.documentSet(1L)
        val doc1 = factory.document(documentSetId=1L, id=(1L << 32) | 0L, title="c", text="foo bar baz oneandtwo oneandthree")
        val doc2 = factory.document(documentSetId=1L, id=(1L << 32) | 1L, title="a", text="moo mar maz oneandtwo twoandthree")
        val doc3 = factory.document(documentSetId=1L, id=(1L << 32) | 2L, title="b", text="noo nar naz oneandthree twoandthree")
        val documents = Vector(doc1, doc2, doc3)

        val documentIds: Vector[Long] = Vector()
        val documentIdsBitSet: Option[immutable.BitSet] = None
        val tagIds: Vector[Long] = Vector()
        val nodeIds: Vector[Long] = Vector()
        val storeObjectIds: Vector[Long] = Vector()
        val tagged: Option[Boolean] = None
        val tagOperation: SelectionRequest.TagOperation = SelectionRequest.TagOperation.Any
        val viewFilterSelections: Vector[ViewFilterSelection] = Vector()
        val q: Option[Query] = None
        val sortByMetadataField: Option[String] = None

        def request = SelectionRequest(
          documentSetId=documentSet.id,
          documentIds=documentIds,
          documentIdsBitSet=documentIdsBitSet,
          tagIds=tagIds,
          nodeIds=nodeIds,
          storeObjectIds=storeObjectIds,
          tagged=tagged,
          viewFilterSelections=viewFilterSelections,
          q=q,
          tagOperation=tagOperation,
          sortByMetadataField=sortByMetadataField
        )

        val searchBackend = mock[SearchBackend]
        val documentBackend = mock[DocumentBackend]
        val viewFilterBackend = mock[ViewFilterBackend]
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

        val documentSetBackend = mock[DocumentSetBackend]
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet))
        val documentIdListBackend = mock[DocumentIdListBackend]

        val backend = new DbDocumentSelectionBackend(
          database,
          documentBackend,
          searchBackend,
          documentSetBackend,
          documentIdListBackend,
          viewFilterBackend,
          Configuration("overview.max_n_regex_documents_per_search" -> 3),
          app.materializer
        )
        def onProgress(p: Double): Unit = {}
        lazy val ret: InMemorySelection = await(backend.createSelection(request, onProgress))
      }

      "show all documents by default" in new CreateSelectionScope {
        ret.documentIds.size must beEqualTo(3)
      }

      "sort documents by title" in new CreateSelectionScope {
        ret.documentIds must beEqualTo(Vector(doc2.id, doc3.id, doc1.id))
      }

      "sort by custom field" in new CreateSelectionScope {
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet.copy(
          metadataSchema=MetadataSchema(1, Vector(MetadataField("foo", MetadataFieldType.String)))
        )))
        override val sortByMetadataField = Some("foo")
        documentIdListBackend.showOrCreate(1, "foo") returns Source(
          List(Progress.Sorting(0.3), Progress.Sorting(0.6), Progress.Sorting(0.9))
        ).mapMaterializedValue(_ => Future.successful(Some(DocumentIdList(5, 1, "foo", Vector(0, 1, 2)))))
        ret.documentIds must beEqualTo(Vector(doc1.id, doc2.id, doc3.id))
      }

      "report progress during sort" in new CreateSelectionScope {
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet.copy(
          metadataSchema=MetadataSchema(1, Vector(MetadataField("foo", MetadataFieldType.String)))
        )))
        override val sortByMetadataField = Some("foo")
        documentIdListBackend.showOrCreate(1, "foo") returns Source(
          List(Progress.Sorting(0.3), Progress.Sorting(0.6), Progress.Sorting(0.9))
        ).mapMaterializedValue(_ => Future.successful(Some(DocumentIdList(5, 1, "foo", Vector(0, 1, 2)))))
        val progressReports = mutable.ArrayBuffer.empty[Double]
        override def onProgress(p: Double): Unit = { progressReports.append(p) }
        ret
        progressReports.toList must beEqualTo(List(0.3, 0.6, 0.9))
      }

      "crash when sort fails" in new CreateSelectionScope {
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet.copy(
          metadataSchema=MetadataSchema(1, Vector(MetadataField("foo", MetadataFieldType.String)))
        )))
        override val sortByMetadataField = Some("foo")
        val ex = new Exception("foo")
        documentIdListBackend.showOrCreate(1, "foo") returns Source.failed(ex).mapMaterializedValue(_ => Future.successful(None))
        ret.documentIds must throwA[Exception]
      }

      "crash when sort gives empty result (because of a race)" in new CreateSelectionScope {
        // TODO maybe this should be a warning? Or maybe we should retry indefinitely?
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet.copy(
          metadataSchema=MetadataSchema(1, Vector(MetadataField("foo", MetadataFieldType.String)))
        )))
        override val sortByMetadataField = Some("foo")
        documentIdListBackend.showOrCreate(1, "foo") returns Source(
          List(Progress.Sorting(0.3), Progress.Sorting(0.6), Progress.Sorting(0.9))
        ).mapMaterializedValue(_ => Future.successful(None))
        ret.documentIds must throwA[Exception]
      }

      "refuse to sort by missing metadata field" in new CreateSelectionScope {
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet.copy(
          metadataSchema=MetadataSchema(1, Vector(MetadataField("bar", MetadataFieldType.String)))
        )))
        override val sortByMetadataField = Some("foo")
        ret.documentIds must beEqualTo(Vector(doc2.id, doc3.id, doc1.id))
        ret.warnings must beEqualTo(List(SelectionWarning.MissingField("foo", List("bar"))))
        there was no(documentIdListBackend).showOrCreate(any, any)
      }

      "search by q" in new CreateSelectionScope {
        override val q = Some(PhraseQuery(Field.All, "moo"))
        override def searchDocumentIds = DocumentIdSet(Vector(doc2.id))
        ret.documentIds must beEqualTo(Vector(doc2.id))
        there was one(searchBackend).search(documentSet.id, q.get)
      }

      "pass through searchindex warnings in q" in new CreateSelectionScope {
        override val q = Some(PhraseQuery(Field.All, "moo"))
        override def searchWarnings = List(
          SearchWarning.TooManyExpansions(Field.Text, "foo", 2),
          SearchWarning.TooManyExpansions(Field.Title, "bar", 2)
        )
        ret.warnings must beEqualTo(searchWarnings.map(w => SelectionWarning.SearchIndexWarning(w)))
      }

      "warn when query has invalid fields" in new CreateSelectionScope {
        // The user might not _know_ that search fields are invalid, so we need
        // to tell them. That involves looking at the MetadataSchema.
        //
        // We needn't shelter our search index from missing fields: races mean
        // we don't even have that option. This is _just_ a usability feature.
        documentSetBackend.show(documentSet.id) returns Future.successful(Some(documentSet.copy(metadataSchema=MetadataSchema(1, Vector(
          MetadataField("bar", MetadataFieldType.String, MetadataFieldDisplay.TextInput),
          MetadataField("baz", MetadataFieldType.String, MetadataFieldDisplay.TextInput)
        )))))
        override val q = Some(AndQuery(Vector(PhraseQuery(Field.Metadata("foo"), "moo"), PhraseQuery(Field.Metadata("foo2"), "moo"))))
        ret.warnings must beEqualTo(List(
          SelectionWarning.MissingField("foo", Vector("bar", "baz")),
          SelectionWarning.MissingField("foo2", Vector("bar", "baz"))
        ))
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
        override val tagIds = Vector(tag1.id)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Vector(doc2.id, doc1.id))
      }

      "search by ANY tagIds" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag1.id, tag2.id)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Vector(doc2.id, doc1.id))
      }

      "search by ALL tagIds" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag1.id, tag2.id)
        override val tagOperation = SelectionRequest.TagOperation.All
        ret.documentIds must beEqualTo(Vector(doc2.id))
      }

      "search by NONE tagIds" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag1.id, tag2.id)
        override val tagOperation = SelectionRequest.TagOperation.None
        ret.documentIds must beEqualTo(Vector(doc3.id))
      }

      "search by tagged=false" in new IndexIdsWithTagsScope {
        override val tagged = Some(false)
        ret.documentIds must beEqualTo(Vector(doc3.id))
      }

      "search by tagged=true" in new IndexIdsWithTagsScope {
        override val tagged = Some(true)
        ret.documentIds must beEqualTo(Vector(doc2.id, doc1.id))
      }

      "search by tagged=false OR a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag2.id)
        override val tagged = Some(false)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Vector(doc2.id, doc3.id))
      }

      "search by tagged=false AND a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag2.id)
        override val tagged = Some(false)
        override val tagOperation = SelectionRequest.TagOperation.All
        ret.documentIds must beEqualTo(Vector.empty)
      }

      "search by NOT tagged=false AND NOT a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag2.id)
        override val tagged = Some(false)
        override val tagOperation = SelectionRequest.TagOperation.None
        ret.documentIds must beEqualTo(Vector(doc1.id))
      }

      "search by tagged=true OR a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag2.id)
        override val tagged = Some(true)
        override val tagOperation = SelectionRequest.TagOperation.Any
        ret.documentIds must beEqualTo(Vector(doc2.id, doc1.id))
      }

      "search by tagged=true AND a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag2.id)
        override val tagged = Some(true)
        override val tagOperation = SelectionRequest.TagOperation.All
        ret.documentIds must beEqualTo(Vector(doc2.id))
      }

      "search by NOT tagged=true AND NOT a tag" in new IndexIdsWithTagsScope {
        override val tagIds = Vector(tag2.id)
        override val tagged = Some(true)
        override val tagOperation = SelectionRequest.TagOperation.None
        ret.documentIds must beEqualTo(Vector(doc3.id))
      }

      "intersect results from search index and database" in new CreateSelectionScope {
        val tag = factory.tag(documentSetId=documentSet.id)
        factory.documentTag(doc1.id, tag.id)
        factory.documentTag(doc2.id, tag.id)

        override val tagIds = Vector(tag.id)
        override val q = Some(PhraseQuery(Field.All, "oneandthree"))
        override def searchDocumentIds = DocumentIdSet(Vector(doc1.id, doc3.id))

        ret.documentIds must beEqualTo(Vector(doc1.id))
      }

      "search by nodeIds" in new CreateSelectionScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        override val nodeIds = Vector(node.id)
        ret.documentIds must beEqualTo(Vector(doc2.id, doc1.id))
      }

      "search by documentIds" in new CreateSelectionScope {
        override val documentIds = Vector(doc1.id)
        ret.documentIds must beEqualTo(Vector(doc1.id))
      }

      "search by documentIdsBitSet" in new CreateSelectionScope {
        override val documentIdsBitSet = Some(immutable.BitSet(doc1.id.toInt))
        ret.documentIds must beEqualTo(Vector(doc1.id))
      }

      "intersect two Postgres filters" in new CreateSelectionScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        val tag = factory.tag(documentSetId=documentSet.id)
        factory.documentTag(doc1.id, tag.id)
        factory.documentTag(doc3.id, tag.id)

        override val nodeIds = Vector(node.id)
        override val tagIds = Vector(tag.id)

        ret.documentIds must beEqualTo(Vector(doc1.id))
      }

      "search by storeObjectIds" in new CreateSelectionScope {
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        val store = factory.store(apiToken=apiToken.token)
        val obj = factory.storeObject(storeId=store.id)
        val dvo1 = factory.documentStoreObject(doc1.id, obj.id)
        val dvo2 = factory.documentStoreObject(doc2.id, obj.id)

        override val storeObjectIds = Vector(obj.id)

        ret.documentIds must beEqualTo(Vector(doc2.id, doc1.id))
      }

      "filter by a ViewFilterSelection" in new CreateSelectionScope {
        val selection = ViewFilterSelection(234, "https://api", Vector("foo"), ViewFilterSelection.Operation.Any)
        override val viewFilterSelections = Vector(selection)
        val anIdSet = DocumentIdSet(Vector(doc1.id, doc3.id))
        viewFilterBackend.resolve(documentSet.id, selection) returns Future.successful(Right(anIdSet))
        ret.documentIds must containTheSameElementsAs(Vector(doc1.id, doc3.id))
        ret.warnings must beEmpty
      }

      "AND ViewFilterSelection selections" in new CreateSelectionScope {
        val selection1 = ViewFilterSelection(234, "https://api", Vector("foo"), ViewFilterSelection.Operation.Any)
        val selection2 = ViewFilterSelection(235, "https://api", Vector("foo"), ViewFilterSelection.Operation.Any)
        override val viewFilterSelections = Vector(selection1, selection2)
        val idSet1 = DocumentIdSet(Vector(doc1.id, doc3.id))
        val idSet2 = DocumentIdSet(Vector(doc1.id, doc2.id))
        viewFilterBackend.resolve(documentSet.id, selection1) returns Future.successful(Right(idSet1))
        viewFilterBackend.resolve(documentSet.id, selection2) returns Future.successful(Right(idSet2))
        ret.documentIds must containTheSameElementsAs(Vector(doc1.id))
        ret.warnings must beEmpty
      }

      "warn and not filter when ViewFilterSelection fails to resolve" in new CreateSelectionScope {
        val selection = ViewFilterSelection(234, "https://api", Vector("foo"), ViewFilterSelection.Operation.Any)
        override val viewFilterSelections = Vector(selection)
        val error = ViewFilterBackend.ResolveError.HttpTimeout("http://foo")
        viewFilterBackend.resolve(documentSet.id, selection) returns Future.successful(Left(error))
        ret.documentIds must containTheSameElementsAs(Vector(doc1.id, doc2.id, doc3.id))
        ret.warnings must beEqualTo(Vector(SelectionWarning.ViewFilterError(error)))
      }

      "Combine one successful ViewFilterSelection with another's warning" in new CreateSelectionScope {
        val selection1 = ViewFilterSelection(234, "https://api", Vector("foo"), ViewFilterSelection.Operation.Any)
        val selection2 = ViewFilterSelection(235, "https://api", Vector("foo"), ViewFilterSelection.Operation.Any)
        override val viewFilterSelections = Vector(selection1, selection2)
        val idSet1 = DocumentIdSet(Vector(doc1.id, doc3.id))
        val error = ViewFilterBackend.ResolveError.HttpTimeout("http://foo")
        viewFilterBackend.resolve(documentSet.id, selection1) returns Future.successful(Right(idSet1))
        viewFilterBackend.resolve(documentSet.id, selection2) returns Future.successful(Left(error))
        ret.documentIds must containTheSameElementsAs(Vector(doc1.id, doc3.id))
        ret.warnings must beEqualTo(Vector(SelectionWarning.ViewFilterError(error)))
      }

      "filter by regex (white-box)" in new CreateSelectionScope {
        val rules = Vector(DocumentSelectionBackend.RegexSearchRule(Field.Title, Pattern.compile("[ac]"), false))
        documentBackend.stream(documentSet.id, documents.map(_.id)) returns Source(documents)
        await(backend.documentIdsMatchingRegexSearchRules(documentSet.id, documents.map(_.id), rules)) must beEqualTo((
          Vector(doc1.id, doc2.id),
          Nil
        ))
      }

      "AND when filtering by regex (white-box)" in new CreateSelectionScope {
        val rules = Vector(
          DocumentSelectionBackend.RegexSearchRule(Field.Title, Pattern.compile("[ac]"), false),
          DocumentSelectionBackend.RegexSearchRule(Field.Title, Pattern.compile("[bc]"), false),
        )
        documentBackend.stream(documentSet.id, documents.map(_.id)) returns Source(documents)
        await(backend.documentIdsMatchingRegexSearchRules(documentSet.id, documents.map(_.id), rules)) must beEqualTo((
          Vector(doc1.id),
          Nil
        ))
      }

      "truncate when filtering by regex (white-box)" in new CreateSelectionScope {
        val rules = Vector(
          DocumentSelectionBackend.RegexSearchRule(Field.Title, Pattern.compile("[ac]"), false),
          DocumentSelectionBackend.RegexSearchRule(Field.Title, Pattern.compile("[bc]"), false),
        )
        // max_n_regex_documents_per_search = 3
        documentBackend.stream(documentSet.id, documents.map(_.id)) returns Source(documents)
        await(backend.documentIdsMatchingRegexSearchRules(documentSet.id, documents.map(_.id) ++ Vector(0L), rules)) must beEqualTo((
          Vector(doc1.id),
          List(SelectionWarning.RegexLimited(4, 3))
        ))
        there was one(documentBackend).stream(documentSet.id, documents.map(_.id))
      }

      "nop-op when there are no regexes (white-box)" in new CreateSelectionScope {
        val rules = Vector()
        val ids = documents.map(_.id) ++ Vector(0L) // MaxNRegexDocumentsPerSearch would limit this if we had rules
        await(backend.documentIdsMatchingRegexSearchRules(documentSet.id, ids, rules)) must beEqualTo((ids, Nil))
        there was no(documentBackend).stream(any, any)
      }

      "filter by regex (black-box)" in new CreateSelectionScope {
        // A bit of an integration-test: test that regex goes alongside other
        // queries and that its warnings aren't ignored
        override val q = Some(AndQuery(Vector(
          PrefixQuery(Field.All, "oneand"),
          RegexQuery(Field.Title, "[bc]"),
          NotQuery(RegexQuery(Field.Title, "inva(id")),
          OrQuery(Vector(
            PhraseQuery(Field.All, "oneandthree"),
            RegexQuery(Field.Title, "will-not-run"),
          ))
        )))
        override def searchDocumentIds = DocumentIdSet(Vector(doc2.id, doc1.id))
        documentBackend.stream(documentSet.id, Vector(doc2.id, doc1.id)) returns Source(Vector(doc2, doc1))
        ret.warnings must beEqualTo(List(
          SelectionWarning.RegexSyntaxError("inva(id", "missing closing )", -1),
          SelectionWarning.NestedRegexIgnored("will-not-run"),
        ))
        ret.documentIds must beEqualTo(Vector(doc1.id))
      }
    }
  }
}
