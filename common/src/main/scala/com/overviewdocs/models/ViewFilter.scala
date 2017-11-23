package com.overviewdocs.models

import play.api.libs.json.JsObject

/** A component that produces document-ID filters.
  *
  * A ViewFilter has both a client-side and a server-side component:
  *
  * * The client-side component is a represented on the server as a bunch
  *   of JSON. We call it `json` here. It's opaque.
  * * The server-side component is a URL. We use it when filtering by a
  *   ViewFilterSelection.
  */
case class ViewFilter(
  url: String,
  json: JsObject
)
