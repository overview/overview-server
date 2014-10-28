package org.overviewproject.models

import play.api.libs.json.JsObject

/** Links an API Token to a Store. */
case class Store(
  id: Long,
  apiToken: String,
  json: JsObject
)
