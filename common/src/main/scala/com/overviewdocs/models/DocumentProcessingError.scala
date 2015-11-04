package com.overviewdocs.models

case class DocumentProcessingError(
  id: Long,
  documentSetId: Long,
  textUrl: String,
  message: String,
  statusCode: Option[Int],
  headers: Option[String]
) {
  def statusMessage: String = statusCode match {
    case None => message
    case Some(100) => "HTTP 100 Continue"
    case Some(101) => "HTTP 101 Switching Protocols"
    case Some(102) => "HTTP 102 Processing"
    case Some(200) => "HTTP 200 OK"
    case Some(201) => "HTTP 201 Created"
    case Some(202) => "HTTP 202 Accepted"
    case Some(203) => "HTTP 203 Non-Authoritative Information"
    case Some(204) => "HTTP 204 No Content"
    case Some(205) => "HTTP 205 Reset Content"
    case Some(206) => "HTTP 206 Partial Content"
    case Some(207) => "HTTP 207 Multi-Status"
    case Some(208) => "HTTP 208 Already Reported"
    case Some(226) => "HTTP 226 IM Used"
    case Some(300) => "HTTP 300 Multiple Choices"
    case Some(301) => "HTTP 301 Moved Permanently"
    case Some(302) => "HTTP 302 Found"
    case Some(303) => "HTTP 303 See Other"
    case Some(304) => "HTTP 304 Not Modified"
    case Some(305) => "HTTP 305 Use Proxy"
    case Some(306) => "HTTP 306 Reserved"
    case Some(307) => "HTTP 307 Temporary Redirect"
    case Some(308) => "HTTP 308 Permanent Redirect"
    case Some(400) => "HTTP 400 Bad Request"
    case Some(401) => "HTTP 401 Unauthorized"
    case Some(402) => "HTTP 402 Payment Required"
    case Some(403) => "HTTP 403 Forbidden"
    case Some(404) => "HTTP 404 Not Found"
    case Some(405) => "HTTP 405 Method Not Allowed"
    case Some(406) => "HTTP 406 Not Acceptable"
    case Some(407) => "HTTP 407 Proxy Authentication Required"
    case Some(408) => "HTTP 408 Request Timeout"
    case Some(409) => "HTTP 409 Conflict"
    case Some(410) => "HTTP 410 Gone"
    case Some(411) => "HTTP 411 Length Required"
    case Some(412) => "HTTP 412 Precondition Failed"
    case Some(413) => "HTTP 413 Request Entity Too Large"
    case Some(414) => "HTTP 414 Request-URI Too Long"
    case Some(415) => "HTTP 415 Unsupported Media Type"
    case Some(416) => "HTTP 416 Requested Range Not Satisfiable"
    case Some(417) => "HTTP 417 Expectation Failed"
    case Some(422) => "HTTP 422 Unprocessable Entity"
    case Some(423) => "HTTP 423 Locked"
    case Some(424) => "HTTP 424 Failed Dependency"
    case Some(425) => "HTTP 425 Unassigned"
    case Some(426) => "HTTP 426 Upgrade Required"
    case Some(427) => "HTTP 427 Unassigned"
    case Some(428) => "HTTP 428 Precondition Required"
    case Some(429) => "HTTP 429 Too Many Requests"
    case Some(430) => "HTTP 430 Unassigned"
    case Some(431) => "HTTP 431 Request Header Fields Too Large"
    case Some(500) => "HTTP 500 Internal Server Error"
    case Some(501) => "HTTP 501 Not Implemented"
    case Some(502) => "HTTP 502 Bad Gateway"
    case Some(503) => "HTTP 503 Service Unavailable"
    case Some(504) => "HTTP 504 Gateway Timeout"
    case Some(505) => "HTTP 505 HTTP Version Not Supported"
    case Some(506) => "HTTP 506 Variant Also Negotiates (Experimental)"
    case Some(507) => "HTTP 507 Insufficient Storage"
    case Some(508) => "HTTP 508 Loop Detected"
    case Some(509) => "HTTP 509 Unassigned"
    case Some(510) => "HTTP 510 Not Extended"
    case Some(511) => "HTTP 511 Network Authentication Required"
    case Some(i) => "HTTP ${i}"
  }
}

object DocumentProcessingError {
  case class CreateAttributes(
    documentSetId: Long,
    textUrl: String,
    message: String,
    statusCode: Option[Int],
    headers: Option[String]
  )
}
