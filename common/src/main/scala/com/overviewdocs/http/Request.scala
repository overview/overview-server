package com.overviewdocs.http

case class Request(
  url: String,
  maybeCredentials: Option[Credentials] = None,
  followRedirects: Boolean = true,
  maybeBody: Option[Array[Byte]] = None
)
