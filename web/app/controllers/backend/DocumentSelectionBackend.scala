package controllers.backend

import akka.stream.scaladsl.Sink
import akka.stream.Materializer
import com.google.inject.ImplementedBy
import com.google.re2j.{Matcher,Pattern,PatternSyntaxException}
import javax.inject.Inject
import play.api.libs.json.JsObject
import play.api.Configuration
import scala.collection.{immutable,mutable}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._ // TODO use another context

import com.overviewdocs.database.Database
import com.overviewdocs.models.{DocumentIdFilter,DocumentIdSet,DocumentSet,Document}
import com.overviewdocs.query.{Field,Query=>SearchQuery,AndQuery,OrQuery,NotQuery,RegexQuery} // "Query" conflicts with SQL Query
import com.overviewdocs.searchindex.SearchResult
import com.overviewdocs.util.Logger
import models.{InMemorySelection,SelectionRequest,SelectionWarning}

/** Constructs an InMemorySelection out of a SelectionRequest.
  *
  * Do not confuse this with other Backends:
  *
  * SelectionBackend loads and saves Selections.
  * DocumentBackend loads and saves Documents.
  * DocumentSelectionBackend creates a Selection by querying services.
  *
  * onProgress() will be called with numbers between 0.0 and 1.0, inclusive. It
  * may be called at any time before the Future is resolved.
  */
@ImplementedBy(classOf[DbDocumentSelectionBackend])
trait DocumentSelectionBackend {
  def createSelection(selectionRequest: SelectionRequest, onProgress: Double => Unit): Future[InMemorySelection]
}

object DocumentSelectionBackend {
  protected[backend] case class RegexSearchRule(
    field: Field,
    pattern: Pattern,
    negated: Boolean
  ) {
    private val matcher = pattern.matcher("") // Not thread-safe ... but we won't use this where that matters

    private def test(input: String) = {
      matcher.reset(input)
      matcher.find()
    }

    def matches(document: Document): Boolean = {
      val textMatches = field match {
        case Field.Title => test(document.title)
        case Field.Text => test(document.text)
        case Field.Notes => document.pdfNotes.pdfNotes.map(_.text).exists(test _)
        case Field.All => test(document.title) || test(document.text)
        case Field.Metadata(fieldName) => {
          // Undefined behavior if fieldName is not in the document schema. (We
          // assume it is -- otherwise there should be a warning.)
          document.metadataJson.value.get(fieldName) // Option[JsValue]
            .flatMap(_.asOpt[String]) // Option[String]
            .map(test _) // Option[Boolean]
            .getOrElse(false)
        }
      }

      textMatches != negated
    }

    override def equals(other: Any): Boolean = other match {
      case RegexSearchRule(otherField, otherPattern, otherNegated) => {
        field == otherField && pattern.toString == otherPattern.toString && negated == otherNegated
      }
      case _ => false
    }
  }

  private def parseRegex(query: RegexQuery, negated: Boolean): Either[SelectionWarning,RegexSearchRule] = {
    try {
      Right(RegexSearchRule(query.field, Pattern.compile(query.regex), negated))
    } catch {
      case ex: PatternSyntaxException => {
        Left(SelectionWarning.RegexSyntaxError(query.regex, ex.getDescription, ex.getIndex))
      }
    }
  }

  private def queryToSearchRuleEithersInner(query: SearchQuery): immutable.Seq[Either[SelectionWarning,RegexSearchRule]] = {
    // Generates a warning for every regex query (Overview doesn't handle
    // nested regex queries)
    query match {
      case RegexQuery(_, regex) => List(Left(SelectionWarning.NestedRegexIgnored(regex)))
      case NotQuery(q) => queryToSearchRuleEithersInner(q)
      case AndQuery(children) => {
        children.flatMap(queryToSearchRuleEithersOuter _)
      }
      case OrQuery(children) => {
        children.flatMap(queryToSearchRuleEithersOuter _)
      }
      case _ => Nil
    }
  }

  private def queryToSearchRuleEithersOuter(query: SearchQuery): immutable.Seq[Either[SelectionWarning,RegexSearchRule]] = {
    query match {
      case rq: RegexQuery => {
        List(parseRegex(rq, false))
      }
      case NotQuery(rq: RegexQuery) => {
        List(parseRegex(rq, true))
      }
      case AndQuery(children) => {
        children.flatMap(queryToSearchRuleEithersOuter _)
      }
      case OrQuery(children) => {
        children.flatMap(queryToSearchRuleEithersInner _)
      }
      case NotQuery(q) => queryToSearchRuleEithersInner(q)
      case _ => Nil
    }
  }

  protected[backend] def queryToRegexSearchRules(query: SearchQuery): (immutable.Seq[RegexSearchRule], List[SelectionWarning]) = {
    val eithers = queryToSearchRuleEithersOuter(query)
    (eithers.flatMap(_.right.toOption).toVector, eithers.flatMap(_.left.toOption).toList)
  }
}

class DbDocumentSelectionBackend @Inject() (
  val database: Database,
  val documentBackend: DocumentBackend,
  val searchBackend: SearchBackend,
  val documentSetBackend: DocumentSetBackend,
  val documentIdListBackend: DocumentIdListBackend,
  val configuration: Configuration,
  val materializer: Materializer
) extends DocumentSelectionBackend with DbBackend {
  import database.api._

  private val MaxNRegexDocumentsPerSearch = configuration.get[Int]("overview.max_n_regex_documents_per_search")

  protected lazy val logger = Logger.forClass(getClass)

  /** Returns all a DocumentSet's Document IDs, sorted. */
  private def getSortedIds(documentSet: DocumentSet, sortByMetadataField: Option[String], onProgress: Double => Unit): Future[(immutable.Seq[Long],List[SelectionWarning])] = {
    logger.logExecutionTimeAsync("fetching sorted document IDs [docset {}, field {}]", documentSet.id, sortByMetadataField) {
      sortByMetadataField match {
        case None => getDefaultSortedIds(documentSet.id).map(ids => (ids, Nil))
        case Some(field) if !validFieldNames(documentSet).contains(field) => {
          getDefaultSortedIds(documentSet.id)
            .map(ids => (ids, List(SelectionWarning.MissingField(field, validFieldNames(documentSet)))))
        }
        case Some(field) => {
          documentIdListBackend.showOrCreate(documentSet.id.toInt, field)
            .toMat(Sink.foreach(progress => onProgress(progress.progress)))((idsFuture, doneFuture) => doneFuture.flatMap(_ => idsFuture))
            .run()(materializer)
            .map(_ match {
              case None => throw new Exception("Sort failed. Look for earlier error messages.")
              case Some(ids) => (ids.toDocumentIds, Nil)
            })
        }
      }
    }
  }

  private def getDefaultSortedIds(documentSetId: Long): Future[immutable.Seq[Long]] = {
    // The ORM is unaware of DocumentSet.sortedDocumentIds
    val q = sql"SELECT sorted_document_ids FROM document_set WHERE id = ${documentSetId}".as[Seq[Long]]
    database.option(q).map(_.map(_.toVector).getOrElse(Vector.empty))
  }

  private def queryMetadataFieldNames(query: SearchQuery): immutable.Set[String] = {
    val fieldNames = mutable.Set.empty[String]
    SearchQuery.walkFields(query)(_ match {
      case Field.Metadata(name) => fieldNames.add(name)
      case _ => {}
    })
    fieldNames.to[immutable.Set]
  }

  private def validFieldNames(documentSet: DocumentSet): List[String] = {
    documentSet.metadataSchema.fields.map(_.name).toList
  }

  private def missingFields(documentSet: DocumentSet, fields: List[String]): List[String] = {
    fields.diff(validFieldNames(documentSet))
  }

  private def missingQueryFieldWarnings(documentSet: DocumentSet, query: SearchQuery): List[SelectionWarning] = {
    val queryFieldNames = queryMetadataFieldNames(query).toList.sorted
    missingFields(documentSet, queryFieldNames)
      .map(name => SelectionWarning.MissingField(name, validFieldNames(documentSet)))
  }

  /** Returns IDs from the searchindex */
  private def indexByQ(documentSet: DocumentSet, query: SearchQuery): Future[(DocumentIdFilter,List[SelectionWarning])] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", query.toString) {
      val documentSetWarnings = missingQueryFieldWarnings(documentSet, query)
      for {
        searchResult <- searchBackend.search(documentSet.id, query)
      } yield {
        val searchResultWarnings = searchResult.warnings.map(w => SelectionWarning.SearchIndexWarning(w))
        (searchResult.documentIds, documentSetWarnings ++ searchResultWarnings)
      }
    }
  }

  /** Returns IDs that match the given tags/objects/etc (everything but
    * search pharse), unsorted.
    */
  private def indexByDB(request: SelectionRequest): Future[DocumentIdSet] = {
    logger.logExecutionTimeAsync("finding document IDs matching '{}'", request.toString) {
      implicit val getInt = slick.jdbc.GetResult(r => r.nextInt)

      database.run(sql"#${idsBySelectionRequestSql(request)}".as[Int]).map { result =>
        val ids = mutable.BitSet.empty
        result.foreach(ids.add _)
        DocumentIdSet(request.documentSetId.toInt, immutable.BitSet.fromBitMaskNoCopy(ids.toBitMask))
      }
    }
  }

  /** Returns a subset of the DocumentSet's Document IDs, sorted. */
  private def indexSelectedIds(request: SelectionRequest, onProgress: Double => Unit): Future[InMemorySelection] = {
    documentSetBackend.show(request.documentSetId).flatMap(_ match {
      case None => Future.successful(InMemorySelection(Array.empty[Long]))
      case Some(documentSet) => {
        val byQFuture: Future[(DocumentIdFilter,List[SelectionWarning])] = request.q match {
          case None => Future.successful((DocumentIdFilter.All(request.documentSetId.toInt), Nil))
          case Some(q) => indexByQ(documentSet, q)
        }

        val byDbFuture: Future[DocumentIdFilter] = if (request.copy(q=None).isAll) {
          Future.successful(DocumentIdFilter.All(request.documentSetId.toInt))
        } else {
          indexByDB(request)
        }

        val byBitSet = request.documentIdsBitSet match {
          case Some(bitSet) => DocumentIdSet(request.documentSetId.toInt, bitSet)
          case None => DocumentIdFilter.All(request.documentSetId.toInt)
        }

        for {
          (allSortedIds, sortWarnings) <- getSortedIds(documentSet, request.sortByMetadataField, onProgress)
          (byQ, byQWarnings) <- byQFuture
          byDb <- byDbFuture
          sortedIds <- Future.successful({
            logger.logExecutionTime("filtering sorted document IDs [docset {}]", request.documentSetId) {
              val idsFilter = byQ.intersect(byDb).intersect(byBitSet)
              allSortedIds.filter(idsFilter.contains _)
            }
          })
          (regexFilteredIds, regexWarnings) <- runRegexFilters(request.documentSetId, sortedIds, request.q)
        } yield {
          InMemorySelection(regexFilteredIds, byQWarnings ++ sortWarnings ++ regexWarnings)
        }
      }
    })
  }

  override def createSelection(request: SelectionRequest, onProgress: Double => Unit): Future[InMemorySelection] = {
    indexSelectedIds(request, onProgress)
  }

  protected def idsBySelectionRequestSql(request: SelectionRequest): String = {
    // Don't have to worry about SQL injection: every SelectionRequest
    // parameter is an ID. (Or it's "q", which this method ignores.)
    val sb = new StringBuilder(s"""SELECT id::BIT(32)::INT4 FROM document WHERE document_set_id = ${request.documentSetId}""")

    if (request.documentIds.nonEmpty) {
      sb.append(s"""
        AND id IN (${request.documentIds.mkString(",")})""")
    }

    if (request.nodeIds.nonEmpty) {
      sb.append(s"""
        AND EXISTS (
          SELECT 1 FROM node_document WHERE document_id = document.id
          AND node_id IN (${request.nodeIds.mkString(",")})
        )""")
    }

    if (request.storeObjectIds.nonEmpty) {
      sb.append(s"""
        AND EXISTS (
          SELECT 1 FROM document_store_object WHERE document_id = document.id
          AND store_object_id IN (${request.storeObjectIds.mkString(",")})
        )""")
    }

    if (request.tagIds.nonEmpty || request.tagged.nonEmpty) {
      val taggedSql = "EXISTS (SELECT 1 FROM document_tag WHERE document_id = document.id)"

      request.tagOperation match {
        case SelectionRequest.TagOperation.Any => {
          val parts = mutable.Buffer[String]()

          if (request.tagIds.nonEmpty) {
            parts.append(s"""EXISTS (
              SELECT 1
              FROM document_tag
              WHERE document_id = document.id
                AND tag_id IN (${request.tagIds.mkString(",")})
            )""")
          }

          request.tagged match {
            case Some(true) => parts.append(taggedSql)
            case Some(false) => parts.append("NOT " + taggedSql)
            case None =>
          }

          sb.append(s" AND (${parts.mkString(" OR ")})")
        }

        case SelectionRequest.TagOperation.All => {
          for (tagId <- request.tagIds) {
            sb.append(s"""
              AND EXISTS (SELECT 1 FROM document_tag WHERE document_id = document.id AND tag_id = $tagId)""")
          }

          request.tagged match {
            case Some(true) => sb.append("\nAND " + taggedSql)
            case Some(false) => sb.append("\nAND NOT " + taggedSql)
            case None =>
          }
        }

        case SelectionRequest.TagOperation.None => {
          if (request.tagIds.nonEmpty) {
            sb.append(s"""
              AND NOT EXISTS (
                SELECT 1
                FROM document_tag
                WHERE document_id = document.id
                  AND tag_id IN (${request.tagIds.mkString(",")})
              )""")
          }

          request.tagged match {
            case Some(true) => sb.append("\nAND NOT " + taggedSql)
            case Some(false) => sb.append("\nAND " + taggedSql)
            case None =>
          }
        }
      }
    }

    sb.toString
  }

  private def runRegexFilters(documentSetId: Long, ids: immutable.Seq[Long], maybeQ: Option[SearchQuery]): Future[(immutable.Seq[Long], List[SelectionWarning])] = {
    maybeQ match {
      case None => Future.successful((ids, Nil))
      case Some(q) => {
        val (rules, parseWarnings) = DocumentSelectionBackend.queryToRegexSearchRules(q)
        for {
          (filteredIds, filterWarnings) <- documentIdsMatchingRegexSearchRules(documentSetId, ids, rules)
        } yield {
          (filteredIds, parseWarnings ++ filterWarnings)
        }
      }
    }
  }

  protected[backend] def documentIdsMatchingRegexSearchRules(documentSetId: Long, ids: immutable.Seq[Long], rules: immutable.Seq[DocumentSelectionBackend.RegexSearchRule]): Future[(immutable.Seq[Long], List[SelectionWarning])] = {
    if (rules.isEmpty) return Future.successful((ids, Nil))

    val (limitedIds, warnings) = if (ids.size > MaxNRegexDocumentsPerSearch) {
      (ids.take(MaxNRegexDocumentsPerSearch), List(SelectionWarning.RegexLimited(ids.size, MaxNRegexDocumentsPerSearch)))
    } else {
      (ids, Nil)
    }

    val source = documentBackend.stream(documentSetId, limitedIds)
      .filter(document => !rules.exists(r => !r.matches(document)))
      .map(_.id)

    source.runWith(Sink.seq)(materializer)
      .map(result => (result, warnings))
  }
}
