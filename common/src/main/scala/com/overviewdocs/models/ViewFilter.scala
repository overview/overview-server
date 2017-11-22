package com.overviewdocs.models

import play.api.libs.json.JsObject
import scala.collection.immutable

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

case class ViewFilterSelection(
  viewId: Long,
  ids: immutable.Seq[String],
  operation: ViewFilterSelection.Operation
)

object ViewFilterSelection {
  sealed trait Operation
  object Operation {
    case object Any extends Operation
    case object All extends Operation
    case object None extends Operation
  }
}
