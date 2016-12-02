package com.overviewdocs.searchindex

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import play.api.libs.json.{JsObject,JsValue,Json}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise}

import com.overviewdocs.http
import com.overviewdocs.models.Document
import com.overviewdocs.query.{AllQuery,Field,Query}
import com.overviewdocs.util.{Configuration,Logger}

/** ElasticSearch index client.
  *
  * We connect via HTTP. We don't use the ElasticSearch client library because
  * it adds more complexity than it removes.
  *
  * Within ElasticSearch, we use these indices and aliases:
  *
  * <ul>
  *   <li><tt>documents_v1</tt> (or <tt>_v2</tt>, etc.) is the actual index.
  *       ElasticSearchIndexClient will create <tt>documents_v1</tt> if there is
  *       no <tt>documents</tt> alias.</li>
  *   <li><tt>documents</tt> is an alias to the <tt>documents_v1</tt>.
  *       ElasticSearchIndexClient reads this alias in order to create other
  *       aliases.</li>
  *   <li><tt>documents_123</tt> is a filtered alias to <tt>documents_v1</tt>,
  *       with <tt>document_set_id = 123</tt>. ElasticSearchIndexClient creates
  *       this within addDocumentSet() and uses it within addDocuments().</li>
  * </ul>
  *
  * We can reindex seamlessly while Overview is running, like this. (All these
  * steps should take place <em>outside</em> ElasticSearchIndexClient.
  * ElasticSearchIndexClient will behave correctly at any point in this
  * process.)
  *
  * <ol>
  *   <li>Create <tt>documents_v2</tt>.</li>
  *   <li>Add a new <tt>document</tt> mapping. It should be a mirror of
  *       <tt>common/src/main/resources/documents-mapping.json</tt>.</li>
  *   <li>For each document set <tt>N</tt>, add a second alias
  *       <tt>documents_N</tt> that points to <tt>documents_v2</tt>.
  *       (So ElasticSearchIndexClient see new documents.)</li>
  *   <li>Modify the <tt>documents</tt> alias. (ElasticSearchIndexClient will
  *       write new documents to <tt>documents_v2</tt>.)</li>
  *   <li>For each document set <tt>N</tt>:
  *     <ol>
  *       <li>Index all documents in document set <tt>N</tt> into
  *           <tt>documents_v2</tt>, from scratch. (<tt>_id</tt> will
  *           ensure you overwrite rather than add documents.)</li>
  *       <li>Drop the alias from <tt>documents_N</tt> to 
  *           any index other than <tt>documents_v2</tt>.</li>
  *     </ol>
  *   </li>
  * </ol>
  *
  * This process will handle resumes and writes that happen during indexing.
  * Only when the <tt>documents_N</tt> alias points to only
  * <tt>documents_v2</tt> is document set <tt>N</tt> complete.
  *
  * @param HTTP hosts to connect to: e.g., `"localhost:9200,192.168.1.2:9200"`
  */
class ElasticSearchIndexClient(val hosts: Seq[String]) extends IndexClient {
  private val logger = Logger.forClass(getClass)

  private val MaxTermExpansions = 1000 // https://groups.google.com/d/topic/overview-dev/CzPGxoOXdCI/discussion
  private lazy val SettingsJson = loadJsonResource("/documents-settings.json")
  private lazy val MappingJson = loadJsonResource("/documents-mapping.json")

  private val HighlightBegin: Char = '\u0001' // something that can't be in any text ever
  private val HighlightEnd: Char = '\u0002'

  class UnexpectedResponse(message: String) extends Exception(message)
  object UnexpectedResponse {
    def apply(json: JsValue): UnexpectedResponse = new UnexpectedResponse(json.toString)
  }

  private[searchindex] val httpClient: http.Client = new http.NingClient

  private case class Request(path: String, maybeBody: Option[JsValue]) {
    def toHttpRequest: http.Request = http.Request(
      hostUrl(path),
      maybeBody=maybeBody.map(_.toString.getBytes(StandardCharsets.UTF_8))
    )
  }
  private case class Response(statusCode: Int, json: JsValue)
  private object Response {
    def fromHttpResponse(httpResponse: http.Response): Response = {
      Response(httpResponse.statusCode, Json.parse(httpResponse.bodyBytes))
    }
  }

  private[searchindex] def hostUrl(path: String): String = s"http://${hosts.head}$path"
  private def GET(path: String): Future[Response] = GET(Request(path, None))
  private def GET(path: String, body: JsValue): Future[Response] = {
    System.err.println(body.toString())
    GET(Request(path, Some(body)))
  }
  private def GET(request: Request): Future[Response] = {
    httpClient.get(request.toHttpRequest).map(Response.fromHttpResponse _)
  }
  private def POST(path: String): Future[Response] = POST(Request(path, None))
  private def POST(path: String, body: JsValue): Future[Response] = POST(Request(path, Some(body)))
  private def POST(request: Request): Future[Response] = {
    httpClient.post(request.toHttpRequest).map(Response.fromHttpResponse _)
  }
  private def POST(path: String, body: String): Future[Response] = {
    val httpRequest = http.Request(url=hostUrl(path), maybeBody=Some(body.getBytes(StandardCharsets.UTF_8)))
    httpClient.post(httpRequest).map(Response.fromHttpResponse _)
  }
  private def PUT(path: String, body: JsValue): Future[Response] = {
    val request = Request(path, Some(body))
    httpClient.put(request.toHttpRequest).map(Response.fromHttpResponse _)
  }
  private def DELETE(path: String): Future[Response] = {
    val request = Request(path, None)
    httpClient.delete(request.toHttpRequest).map(Response.fromHttpResponse _)
  }
  private def DELETE(path: String, body: JsValue): Future[Response] = {
    val request = Request(path, Some(body))
    httpClient.delete(request.toHttpRequest).map(Response.fromHttpResponse _)
  }
  private def expectOk(response: Response): Unit = response match {
    case Response(200, _) | Response(201, _) => ()
    case Response(_, json) => throw UnexpectedResponse(json)
  }
  private def expectOkOrNotFound(response: Response): Unit = response match {
    case Response(200, _) | Response(201, _) | Response(404, _) => ()
    case Response(_, json) => throw UnexpectedResponse(json)
  }

  /** Number of documents to receive per shard per page.
    *
    * &gt; 10K seems to carry a speed penalty on a similar operation:
    * https://groups.google.com/d/topic/overview-dev/orgV1iDS9U4/discussion
    */
  private val DefaultScrollSize: Int = 5000

  /** Timeout that will kill communications from shards, in milliseconds.
    *
    * The higher this number, the more reliable Overview is. The lower the
    * number, the less time shards will maintain data structures for each
    * query.
    */
  private val ScrollTimeout: Int = 30000

  private def aliasName(documentSetId: Long) = s"documents_$documentSetId"

  private def loadJsonResource(path: String): JsValue = {
    val inputStream = getClass.getResourceAsStream(path)
    val ret = Json.parse(inputStream)
    inputStream.close
    ret
  }

  /** Returns the name of the index where we write new documents.
    *
    * If there is no <tt>documents</tt> alias in ElasticSearch, this method
    * will create a new <tt>documents_v1</tt> index and <tt>documents</tt>
    * alias and return <tt>"documents_v1"</tt>>
    */
  private def getAllDocumentsIndexName: Future[String] = {
    GET("/_alias/documents").flatMap(_ match {
      case Response(200, json) => Future.successful(json.as[JsObject].keys.head)
      case Response(404, _) => createDefaultIndexWithAlias
      case Response(_, json) => throw UnexpectedResponse(json)
    })
  }

  /** Creates a "documents_v1" index with a "documents" alias.
    */
  private def createDefaultIndexWithAlias: Future[String] = {
    PUT("/documents_v1", Json.obj(
      "mappings" -> MappingJson,
      "settings" -> SettingsJson,
      "aliases" -> Json.obj("documents" -> Json.obj())
    )).map(expectOk).map(_ => "documents_v1")
  }

  override def addDocumentSet(id: Long): Future[Unit] = {
    for {
      indexName <- getAllDocumentsIndexName
      response <- PUT(s"/$indexName/_alias/documents_${id}", Json.obj(
        "filter" -> Json.obj(
          "term" -> Json.obj(
            "document_set_id" -> id
          )
        )
      )).map(expectOk)
    } yield ()
  }

  override def removeDocumentSet(id: Long): Future[Unit] = {
    // Note: if we're reindexing into documents_v2 while we call this method,
    // the documents won't be deleted from documents_v1. But that's okay, since
    // we're going to delete documents_v1 _entirely_ soon, and there won't be
    // any alias pointing towards it.
    //
    // (remember: "documents_v2" is an *index*, "documents" is an *alias*)

    for {
      _ <- refresh // In case we're deleting immediately after some adds
      documentIds <- searchForIds(id, AllQuery)
      indexName <- getAllDocumentsIndexName
      _ <- deleteDocuments(indexName, documentIds)
      _ <- DELETE(s"/_all/_alias/documents_$id").map(expectOkOrNotFound)
    } yield ()
  }

  private def deleteDocuments(indexName: String, documentIds: Seq[Long]): Future[Unit] = {
    if (documentIds.isEmpty) return Future.successful(())

    val commands: Seq[String] = documentIds.map { documentId =>
      Json.obj("delete" -> Json.obj(
        "_index" -> indexName,
        "_type" -> "document",
        "_id" -> documentId
      )).toString + "\n"
    }
    POST("/_bulk", commands.mkString).map(_ match {
      case Response(statusCode, json) if statusCode >= 200 && statusCode < 300 => {
        import play.api.libs.json._
        if ((json \ "errors").as[Boolean] != false) {
          throw UnexpectedResponse(json)
        }
      }
      case Response(404, _) => {} // the index doesn't exist
      case Response(_, json) => throw UnexpectedResponse(json)
    })
  }

  override def addDocuments(documents: Iterable[Document]): Future[Unit] = {
    if (documents.isEmpty) return Future.successful(())

    /*
     * We write to the all-documents alias, not the docset alias. This is
     * important during reindexing: "It is an error to index to an alias which
     * points to more than one index."
     * http://www.elasticsearch.org/guide/en/elasticsearch/reference/current/indices-aliases.html
     */

    val commands: Iterable[String] = documents.map { document =>
      Json.obj(
        "index" -> Json.obj(
          "_index" -> "documents",
          "_type" -> "document",
          "_id" -> document.id.toString
        )
      ).toString + "\n" + Json.obj(
        "document_set_id" -> document.documentSetId,
        "text" -> document.normalizedText,
        "title" -> document.title,
        "supplied_id" -> document.suppliedId
      ).toString + "\n"
    }

    POST("/_bulk", commands.mkString).map(_ match {
      case Response(statusCode, json) if statusCode >= 200 && statusCode < 300 => {
        import play.api.libs.json._
        if ((json \ "errors").as[Boolean] != false) {
          throw UnexpectedResponse(json)
        }
      }
      case Response(_, json) => throw UnexpectedResponse(json)
    })
  }

  /** Finds Highlights in the given text.
    *
    * The given text has highlights delimited by <tt>\u0001</tt> and
    * <tt>\u0002</tt>. We return Highlights that <em>ignore</em> those values:
    * that means the indices we return in the Highlights are less than or
    * equal to the indices in the input text.
    *
    * @param textWithHighlights Text we're searching in
    */
  private def findHighlights(textWithHighlights: String): Seq[Highlight] = {

    /** Searches for "\u0001" and "\u0002" and uses them to create a Highlight.
      *
      * @param textWithHighlights Text we're searching in
      * @param cur                Index into text
      * @param n                  How many highlights came before this one
      */
    def findHighlight(textWithHighlights: String, cur: Int, n: Int): Option[Highlight] = {
      val begin = textWithHighlights.indexOf(HighlightBegin, cur)
      if (begin == -1) {
        None
      } else {
        val end = textWithHighlights.indexOf(HighlightEnd, begin)
        if (end == -1) throw new Exception(s"Found begin without end starting at index ${begin} in text: ${textWithHighlights}")
        Some(Highlight(begin - n * 2, end - n * 2 - 1))
      }
    }

    /** Recursively finds Highlights in the given text.
      *
      * @param textWithHighlights Text we're searching in
      * @param cur                Index into the text
      * @param n                  Number of highlights we've found already
      * @param acc                Return value we're building
      */
    @scala.annotation.tailrec
    def findHighlightsRec(textWithHighlights: String, cur: Int, n: Int, acc: List[Highlight]): List[Highlight] = {
      findHighlight(textWithHighlights, cur, n) match {
        case None => acc.reverse
        case Some(highlight) => findHighlightsRec(textWithHighlights, highlight.end + n * 2 + 2, n + 1, highlight :: acc)
      }
    }

    findHighlightsRec(textWithHighlights, 0, 0, Nil)
  }

  override def highlights(documentSetId: Long, documentIds: Seq[Long], q: Query): Future[Map[Long, Seq[Snippet]]] =  {


    //   '{"query":{"ids":{"type":"document","values":["4294967298","4294967297"]}},"highlight":
//      {"number_of_fragments":1,"require_field_match":false,"fields":{"text":{"highlight_query":{"constant_score":
//      {"filter":{"match_phrase":{"_all":"this"}}}},"pre_tags":["\u0001"],"post_tags":["\u0002"],"number_of_fragments":5}}}}'
//


    // ?filter_path=hits.hits.highlight.text
    GET(s"/documents_$documentSetId/_search", Json.obj(
      "query" -> Json.obj("ids" -> Json.obj("type" -> "document", "values" -> documentIds.map(_.toString))),
      "highlight" -> Json.obj(
        "number_of_fragments" -> 0,
        "require_field_match" -> false, // Confusing: we use *filters*, not *queries*, so true matches nothing
        "fields" -> Json.obj(
          "text" -> Json.obj(
            "highlight_query" -> repr(q),
            "pre_tags" -> Json.arr(HighlightBegin.toString),
            "post_tags" -> Json.arr(HighlightEnd.toString),
            "number_of_fragments" -> 2
          )
        )
      )
    )).map(_ match {
      case Response(statusCode, json) if statusCode >= 200 && statusCode < 300 => {
        import play.api.libs.json._

        val hits = (json \ "hits" \ "hits").as[JsArray]

        hits.value.map { hit =>
          val documentId = (hit \ "_id").as[String].toLong

          val texts = (hit \ "highlight" \ "text").as[JsArray]
          val snippets =
            texts.value
              .map(_.as[String])
              .map(text => Snippet(text.replaceAll(s"$HighlightBegin|$HighlightEnd", ""), findHighlights(text)))

          documentId -> snippets
        }.toMap
      }
      case Response(_, json) => throw UnexpectedResponse(json)
    })
  }

  override def highlight(documentSetId: Long, documentId: Long, q: Query): Future[Seq[Highlight]] = {

    GET(s"/documents_$documentSetId/_search", Json.obj(
      "query" -> Json.obj("ids" -> Json.obj("type" -> "document", "values" -> Json.arr(documentId.toString))),
      "highlight" -> Json.obj(
        "number_of_fragments" -> 0,
        "require_field_match" -> false, // Confusing: we use *filters*, not *queries*, so true matches nothing
        "fields" -> Json.obj(
          "text" -> Json.obj(
            "highlight_query" -> repr(q),
            "pre_tags" -> Json.arr(HighlightBegin.toString),
            "post_tags" -> Json.arr(HighlightEnd.toString),
            "number_of_fragments" -> 0
          )
        )
      )
    )).map(_ match {
      case Response(statusCode, json) if statusCode >= 200 && statusCode < 300 => {
        import play.api.libs.json._
        val path = ((JsPath \ "hits" \ "hits")(0) \ "highlight" \ "text")(0)
        path.asSingleJson(json).asOpt[String] match {
          case Some(fragments) => findHighlights(fragments)
          case None => Seq()
        }
      }
      case Response(_, json) => throw UnexpectedResponse(json)
    })
  }

    private def repr(field: Field): String = field match {
    case Field.All => "_all"
    case Field.Title => "title"
    case Field.Text => "text"
  }

  private def constantScore(keyvals: (String,Json.JsValueWrapper)*): JsValue = {
    Json.obj("constant_score" -> Json.obj("filter" -> Json.obj(keyvals: _*)))
  }

  private def repr(query: Query): JsValue = {
    import com.overviewdocs.query._
    query match {
      case AllQuery => Json.obj("match_all" -> Json.obj())
      case AndQuery(left, right) => Json.obj("bool" ->
        Json.obj("filter" -> Json.arr(repr(left), repr(right)))
      )
      case OrQuery(left, right) => constantScore(
        "bool" -> Json.obj("should" -> Json.arr(repr(left), repr(right)))
      )
      case NotQuery(inner) => Json.obj("bool" -> Json.obj("must_not" -> repr(inner)))
      case PhraseQuery(field, phrase) => constantScore(
        "match_phrase" -> Json.obj(repr(field) -> phrase)
      )
      case PrefixQuery(field, prefix) => constantScore(
        "match_phrase_prefix" -> Json.obj(
          repr(field) -> Json.obj(
            "query" -> prefix,
            "max_expansions" -> MaxTermExpansions
          )
        )
      )
      case ProximityQuery(field, phrase, slop) => constantScore(
        "match_phrase" -> Json.obj(
          repr(field) -> Json.obj(
            "query" -> phrase,
            "slop" -> slop
          )
        )
      )
      case FuzzyTermQuery(field, term, fuzziness) => constantScore(
        "match" -> Json.obj(
          repr(field) -> Json.obj(
            "query" -> term,
            "fuzziness" -> fuzziness.fold("AUTO")(_.toString),
            "max_expansions" -> MaxTermExpansions
          )
        )
      )
    }
  }

  private case class SearchResult(scrollId: String, total: Int, ids: Seq[Long])
  private object SearchResult {
    private val ScrollIdPattern = Pattern.compile(""""_scroll_id":"([^"]+)"""")
    private val TotalPattern = Pattern.compile(""""hits":\{"total":(\d+)""")
    private val IdPattern = Pattern.compile(""""_id":"(\d+)"""")

    /** Parses JSON ... without all the messy objects.
      *
      * (This seems easier to me than writing type-safe JSON.)
      */
    def parse(bytes: Array[Byte], maxNIds: Int): SearchResult = {
      val string = new String(bytes, StandardCharsets.US_ASCII)

      val scrollIdMatcher = ScrollIdPattern.matcher(string)
      val matchedScrollId = scrollIdMatcher.find(); assert(matchedScrollId, "parsing _scroll_id from ElasticSearch")
      val scrollId: String = scrollIdMatcher.group(1)

      val totalMatcher = TotalPattern.matcher(string)
      val matchedTotal = totalMatcher.find(); assert(matchedTotal, "parsing total from ElasticSearch")
      val total: Int = totalMatcher.group(1).toInt

      val ids: Array[Long] = new Array(math.min(total, maxNIds))
      var i: Int = 0
      val idMatcher = IdPattern.matcher(string)
      while (idMatcher.find) {
        ids(i) = idMatcher.group(1).toLong
        i += 1
      }

      SearchResult(scrollId, total, ids)
    }
  }

  /** Request the first batch of document IDs that match `q`.
    *
    * If `retval._2.length < scrollSize`, then you do not need any further
    * requests.
    */
  private def startSearch(documentSetId: Long, q: Query, scrollSize: Int): Future[SearchResult] = {
    httpClient.get(http.Request(
      url=hostUrl(s"/documents_$documentSetId/document/_search?scroll=${ScrollTimeout}ms"),
      maybeBody=Some(Json.obj(
        "query" -> repr(q),
        "size" -> scrollSize,
        "sort" -> "_doc",
        "fields" -> Json.arr("_id")
      ).toString.getBytes(StandardCharsets.UTF_8))
    )).map(_ match {
      case http.Response(200, _, bytes) => SearchResult.parse(bytes, scrollSize)
      case http.Response(404, _, _) => SearchResult("", 0, Seq())
      case http.Response(_, _, bytes) => throw UnexpectedResponse(Json.parse(bytes))
    })
  }

  /** Request a subsequent batch of document IDs with the same `scrollId`.
    *
    * Add up all the document IDs of all the documents; when the total number of
    * IDs exceeds `total`, you need no further requests.
    */
  private def continueSearch(scrollId: String, scrollSize: Int): Future[SearchResult] = {
    httpClient.get(http.Request(
      url=hostUrl(s"/_search/scroll"),
      maybeBody=Some(Json.obj(
        "scroll" -> s"${ScrollTimeout}ms",
        "scroll_id" -> scrollId
      ).toString.getBytes(StandardCharsets.UTF_8))
    )).map(_ match {
      case http.Response(200, _, bytes) => SearchResult.parse(bytes, scrollSize)
      case http.Response(_, _, bytes) => throw UnexpectedResponse(Json.parse(bytes))
    })
  }

  /** Recursively, asynchronously calls continueSearch() until it receives
    * the last page of results, then returns all the IDs.
    */
  private def completeSearch(results: Seq[SearchResult]): Future[Seq[Long]] = {
    val nTotal: Int = results.head.total
    val scrollSize: Int = results.head.ids.length
    val nReceived: Int = scrollSize * (results.length - 1) + results.last.ids.length
    if (nReceived < nTotal) {
      continueSearch(results.head.scrollId, scrollSize).flatMap { resultN: SearchResult => completeSearch(results ++ Seq(resultN)) }
    } else {
      Future.successful(results.map(_.ids).flatten)
    }
  }

  private def endSearch(scrollId: String): Future[Unit] = {
    if (scrollId == "") {
      // If the document set does not exist, ElasticSearch will return a 404,
      // without a scrollId. So we set scrollId="" when that happens.
      Future.successful(())
    } else {
      DELETE("/_search/scroll", Json.obj("scroll_id" -> Json.arr(scrollId))).map(expectOk)
    }
  }

  def searchForIds(documentSetId: Long, q: Query, scrollSize: Int): Future[Seq[Long]] = {
    for {
      result1 <- startSearch(documentSetId, q, scrollSize)
      ids <- completeSearch(Seq(result1))
      _ <- endSearch(result1.scrollId)
    } yield ids
  }

  override def refresh: Future[Unit] = {
    POST("/documents/_refresh").map(expectOk)
  }

  override def deleteAllIndices: Future[Unit] = {
    DELETE("/_all").map(expectOk)
  }

  override def searchForIds(documentSetId: Long, q: Query) = {
    searchForIds(documentSetId, q, DefaultScrollSize)
  }
}

object ElasticSearchIndexClient {
  lazy val singleton = {
    val hosts: Seq[String] = Configuration.getString("search_index.hosts").split(",")
    hosts.filter(_.contains(":9300")).foreach { host =>
      throw new Exception(s"You have configured Overview to connect to ElasticSearch at http://$host. ElasticSearch serves its own protocol on port 9300; it serves HTTP on port 9200. Change your configuration to 9200.")
    }
    new ElasticSearchIndexClient(hosts)
  }
}
