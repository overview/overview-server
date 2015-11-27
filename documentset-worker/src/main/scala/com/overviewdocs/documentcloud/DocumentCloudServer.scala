package com.overviewdocs.documentcloud

import com.fasterxml.jackson.core.JsonProcessingException
import play.api.libs.json.{JsValue,Json}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future,Promise}
import scala.util.{Failure,Success}

import com.overviewdocs.http.{Client,Credentials,Request,Response}

/** Interface to a DocumentCloud HTTP server. */
trait DocumentCloudServer {
  protected val httpClient: Client

  private def maybeCredentials(username: String, password: String): Option[Credentials] = {
    if (username.nonEmpty && password.nonEmpty) {
      Some(Credentials(username, password))
    } else {
      None
    }
  }

  private def queryUrl(relativePath: String): String = {
    "https://www.documentcloud.org/api" + relativePath
  }

  /** Takes a DocumentCloud search-results page (or garbage input) and returns
    * an import-ID-list CSV.
    */
  private def parseImportIdList(bytes: Array[Byte]): Either[String,(IdList,Int)] = {
    parseJson(bytes).right.flatMap(jsonToImportIdList)
  }

  private def parseJson(bytes: Array[Byte]): Either[String,JsValue] = {
    try {
      Right(Json.parse(bytes))
    } catch {
      case _: JsonProcessingException => Left("DocumentCloud produced invalid JSON")
    }
  }

  private def jsonToImportIdList(json: JsValue): Either[String,(IdList,Int)] = {
    Left("Overview failed to parse DocumentCloud's JSON")
  }

  private def httpGet(
    url: String,
    username: String,
    password: String,
    followRedirects: Boolean
  ): Future[Either[String,Response]] = {
    val promise = Promise[Either[String,Response]]()

    httpClient.get(Request(url, maybeCredentials(username, password), followRedirects)).onComplete {
      case Success(response) if response.statusCode == 200 => {
        promise.success(Right(response))
      }
      case Success(response) => {
        promise.success(Left(s"DocumentCloud responded with HTTP ${response.statusCode}"))
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
    val promise = Promise[Either[String,(IdList,Int)]]()

    httpClient.get(Request(queryUrl(query), maybeCredentials(username, password), false)).onComplete {
      case Success(response) if response.statusCode == 200 => {
        promise.success(parseImportIdList(response.bodyBytes))
      }
      case Success(response) => {
        promise.success(Left(s"DocumentCloud responded with HTTP ${response.statusCode}"))
      }
      case Failure(ex) => {
        promise.success(Left(ex.getMessage))
      }
    }

    promise.future
  }

//  /** Returns the text of a document.
//    *
//    * Returns a `Left` if anything happens. Possibilities:
//    *
//    * * `Left("Request to DocumentCloud timed out")`
//    * * `Left("DocumentCloud responded with HTTP 403 Forbidden")`
//    * * `Right("This is the document\nwhee!")`
//    *
//    * @param url URL, from IdList
//    * @param username DocumentCloud credentials, or `""`
//    * @param password DocumentCloud credentials, or `""`
//    * @param public If `true`, don't send credentials; if `false`, follow a
//    *               redirect manually and don't pass the credentials to the
//    *               second page.
//    */
//  def getText(
//    url: String,
//    username: String,
//    password: String,
//    public: Boolean
//  ): Future[Either[String,String]] = {
//    httpGet(url, maybeCredentials(username, password), 
//  }
}
