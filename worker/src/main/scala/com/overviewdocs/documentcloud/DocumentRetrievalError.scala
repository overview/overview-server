package com.overviewdocs.documentcloud

/** Information about documents that could not be retrieved */
case class DocumentRetrievalError(
  /** The requested URL. */
  url: String,

  /** The server response, or the exception text.
    *
    * Neither is in the user's language, but we can't translate server
    * responses anyway, so let's forget about that issue.
    **/
  message: String,

  /** If the server responded, its (non-200) status code. */
  statusCode: Option[Int],

  /** If the server responded, all its headers. */
  headers: Option[Map[String,Seq[String]]]
)
