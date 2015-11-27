package com.overviewdocs.documentcloud

import com.fasterxml.jackson.core.JsonProcessingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException
import play.api.libs.json.{JsValue,Json}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise}
import scala.util.{Failure,Success}

import com.overviewdocs.http
import com.overviewdocs.util.{Configuration,Textify}

/** Interface to a DocumentCloud HTTP server. */
trait DocumentCloudServer {
  protected val httpClient: http.Client
  protected val apiBaseUrl: String = Configuration.getString("documentcloud_url") + "/api"

  /** DocumentCloud only returns UTF-8.
    *
    * Issue https://github.com/documentcloud/documentcloud/issues/221 means
    * we can't let the HTTP client decode the bytes. The HTTP client is rigid
    * about "standards". Sheesh. We and DocumentCloud know better: send invalid
    * headers just to mess with people.
    */
  private val DocumentCloudCharset = StandardCharsets.UTF_8

  private def maybeCredentials(username: String, password: String): Option[http.Credentials] = {
    if (username.nonEmpty && password.nonEmpty) {
      Some(http.Credentials(username, password))
    } else {
      None
    }
  }

  private def queryUrl(q: String): String = {
    apiBaseUrl + "/search.json?q=" + URLEncoder.encode(q, "utf-8")
  }

  /** Takes a DocumentCloud search-results page (or garbage input) and returns
    * an import-ID-list CSV.
    */
  private def parseIdList(bytes: Array[Byte]): Either[String,(IdList,Int)] = {
    parseJson(bytes)
      .right.flatMap(jsonToImportIdList)
  }

  private def parseJson(bytes: Array[Byte]): Either[String,JsValue] = {
    try {
      Right(Json.parse(bytes))
    } catch {
      case _: JsonProcessingException => Left("DocumentCloud responded with invalid JSON")
    }
  }

  private def jsonToImportIdList(json: JsValue): Either[String,(IdList,Int)] = {
    IdList.parseDocumentCloudSearchResult(json)
      .toRight("Overview failed to parse DocumentCloud's JSON")
  }

  /** Gets the given URL, returning the body iff the HTTP status code is 200.
    *
    * Returns a `Left` if anything happens. Possibilities:
    *
    * * `Left("Request to DocumentCloud timed out")`
    * * `Left("DocumentCloud responded with HTTP 403 Forbidden")`
    */
  private def httpGet(
    url: String,
    username: String,
    password: String,
    followRedirects: Boolean
  ): Future[Either[String,Array[Byte]]] = {
    val promise = Promise[Either[String,Array[Byte]]]()

    httpClient.get(http.Request(url, maybeCredentials(username, password), followRedirects)).onComplete {
      case Success(response) if response.statusCode == 200 => {
        promise.success(Right(response.bodyBytes))
      }
      case Success(response) => {
        promise.success(Left(s"DocumentCloud responded with ${http.StatusCodes.describe(response.statusCode)}"))
      }
      case Failure(_: TimeoutException) => {
        promise.success(Left("Request to DocumentCloud timed out"))
      }
      case Failure(ex) => {
        promise.success(Left(ex.getMessage))
      }
    }

    promise.future
  }

  /** Conducts a `/search.json` query on the server; returns the first page of
    * results and a count of the total number of pages to fetch.
    *
    * Returns a `Left` if anything happens. Possibilities:
    *
    * * `Left("Request to DocumentCloud timed out")`
    * * `Left("DocumentCloud responded with HTTP 403 Forbidden")`
    * * `Left("DocumentCloud responded with invalid JSON")`
    * * `Left("Overview failed to parse DocumentCloud's JSON")`
    * * `Right(...)`
    */
  def getIdList0(
    query: String,
    username: String,
    password: String
  ): Future[Either[String,(IdList,Int)]] = {
    getIdListImpl(query, username, password, 0)
  }

  /** Conducts a `/search.json` query for any page other than page 0.
    *
    * Use getIdList0() to determine which page numbers to pass to this method.
    *
    * Returns a `Left` if anything happens. Possibilities:
    *
    * * `Left("Request to DocumentCloud timed out")`
    * * `Left("DocumentCloud responded with HTTP 403 Forbidden")`
    * * `Left("DocumentCloud responded with invalid JSON")`
    * * `Left("Overview failed to parse DocumentCloud's JSON")`
    * * `Right(...)`
    */
  def getIdList(
    query: String,
    username: String,
    password: String,
    pageNumberBase0: Int
  ): Future[Either[String,IdList]] = {
    getIdListImpl(query, username, password, pageNumberBase0).map(_.right.map(_._1))
  }

  private def getIdListImpl(
    query: String,
    username: String,
    password: String,
    pageNumberBase0: Int
  ): Future[Either[String,(IdList,Int)]] = {
    val url = s"${queryUrl(query)}&page=${pageNumberBase0 + 1}&per_page=1000"

    httpGet(url, username, password, false).map(_ match {
      case Right(bytes) => parseIdList(bytes)
      case Left(error) => Left(error)
    })
  }

  /** Returns the text of a document.
    *
    * Returns a `Left` if anything happens. Possibilities:
    *
    * * `Left("Request to DocumentCloud timed out")`
    * * `Left("DocumentCloud responded with HTTP 403 Forbidden")`
    * * `Right("This is the document\nwhee!")`
    *
    * @param url URL, from IdList
    * @param username DocumentCloud credentials, or `""`
    * @param password DocumentCloud credentials, or `""`
    * @param public If `true`, don't send credentials; if `false`, follow a
    *               redirect manually and don't pass the credentials to the
    *               second page.
    */
  def getText(
    url: String,
    username: String,
    password: String,
    access: String
  ): Future[Either[String,String]] = {
    httpGet(url, username, password, access != "public").map { response =>
      response.right.map(bytes => Textify(bytes, DocumentCloudCharset))
    }
  }
}
