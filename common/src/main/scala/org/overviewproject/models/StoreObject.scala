package org.overviewproject.models

import play.api.libs.json.JsObject

/** Something in a Store. (Like a "tag")
  *
  * A single Store (that is, one API key's private data) may store arbitrary
  * objects. This class represents a compromise between performance and
  * flexibility.
  *
  * A caller may:
  *
  * * Index StoreObjects by String or Long (e.g., "find me the tag named 'moo'")
  * * Save arbitrary data for each object (e.g., "name": "my-great-tag")
  * * Save multiple types of objects (by using indexedLong or indexedString to
  *   differentiate them)
  */
case class StoreObject(
  id: Long,
  storeId: Long,
  indexedLong: Option[Long],
  indexedString: Option[String],
  json: JsObject
) {
  def update(attributes: StoreObject.UpdateAttributes): StoreObject = copy(
    indexedLong=attributes.indexedLong,
    indexedString=attributes.indexedString,
    json=attributes.json
  )
}

object StoreObject {
  case class CreateAttributes(
    indexedLong: Option[Long],
    indexedString: Option[String],
    json: JsObject
  )

  case class UpdateAttributes(
    indexedLong: Option[Long],
    indexedString: Option[String],
    json: JsObject
  )

  def build(id: Long, storeId: Long, attributes: CreateAttributes): StoreObject = StoreObject(
    id,
    storeId,
    attributes.indexedLong,
    attributes.indexedString,
    attributes.json
  )
}
