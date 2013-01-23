package models

import models.orm.Schema.documentProcessingErrors
import org.overviewproject.tree.orm.DocumentProcessingError

trait OverviewDocumentProcessingError {
  val statusCode: Option[Int]
  val url: String
}

object OverviewDocumentProcessingError {
  def sortedByStatus(documentSetId: Long): Seq[(String, Seq[OverviewDocumentProcessingError])] = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    val errors = documentProcessingErrors.where(dpe => dpe.documentSetId === documentSetId).map(new OverviewDocumentProcessingErrorImpl(_))

    val errorGroups = errors.groupBy(_.statusCode.getOrElse(-1))
    val errorGroupsSortedByStatusCode = errorGroups.toSeq.sortBy(_._1)
    errorGroupsSortedByStatusCode.map(e => (message(e._1), e._2.toSeq))
  }

  private def message(statusCode: Int): String = 
    if (statusCode == -1) InternalError
    else "%d %s".format(statusCode, HttpStatusCode.getOrElse(statusCode, UnknownStatus))
    
  private class OverviewDocumentProcessingErrorImpl(documentProcessingError: DocumentProcessingError) extends OverviewDocumentProcessingError {
    val statusCode: Option[Int] = documentProcessingError.statusCode
    val url: String = documentProcessingError.textUrl
  }

  private val InternalError: String = "Overview Internal Error"
  private val UnknownStatus: String = "Unknown Status"
  private val HttpStatusCode: Map[Int, String] = Map(
    (100 -> "Continue"),
    (101 -> "Switching Protocols"),
    (102 -> "Processing"),
    (200 -> "OK"),
    (201 -> "Created"),
    (202 -> "Accepted"),
    (203 -> "Non-Authoritative Information"),
    (204 -> "No Content"),
    (205 -> "Reset Content"),
    (206 -> "Partial Content"),
    (207 -> "Multi-Status"),
    (208 -> "Already Reported"),
    (226 -> "IM Used"),
    (300 -> "Multiple Choices"),
    (301 -> "Moved Permanently"),
    (302 -> "Found"),
    (303 -> "See Other"),
    (304 -> "Not Modified"),
    (305 -> "Use Proxy"),
    (306 -> "Reserved"),
    (307 -> "Temporary Redirect"),
    (308 -> "Permanent Redirect"),
    (400 -> "Bad Request"),
    (401 -> "Unauthorized"),
    (402 -> "Payment Required"),
    (403 -> "Forbidden"),
    (404 -> "Not Found"),
    (405 -> "Method Not Allowed"),
    (406 -> "Not Acceptable"),
    (407 -> "Proxy Authentication Required"),
    (408 -> "Request Timeout"),
    (409 -> "Conflict"),
    (410 -> "Gone"),
    (411 -> "Length Required"),
    (412 -> "Precondition Failed"),
    (413 -> "Request Entity Too Large"),
    (414 -> "Request-URI Too Long"),
    (415 -> "Unsupported Media Type"),
    (416 -> "Requested Range Not Satisfiable"),
    (417 -> "Expectation Failed"),
    (422 -> "Unprocessable Entity"),
    (423 -> "Locked"),
    (424 -> "Failed Dependency"),
    (425 -> "Unassigned"),
    (426 -> "Upgrade Required"),
    (427 -> "Unassigned"),
    (428 -> "Precondition Required"),
    (429 -> "Too Many Requests"),
    (430 -> "Unassigned"),
    (431 -> "Request Header Fields Too Large"),
    (500 -> "Internal Server Error"),
    (501 -> "Not Implemented"),
    (502 -> "Bad Gateway"),
    (503 -> "Service Unavailable"),
    (504 -> "Gateway Timeout"),
    (505 -> "HTTP Version Not Supported"),
    (506 -> "Variant Also Negotiates (Experimental)"),
    (507 -> "Insufficient Storage"),
    (508 -> "Loop Detected"),
    (509 -> "Unassigned"),
    (510 -> "Not Extended"),
    (511 -> "Network Authentication Required"))
}
