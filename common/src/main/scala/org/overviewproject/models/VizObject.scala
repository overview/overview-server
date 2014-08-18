package org.overviewproject.models

import play.api.libs.json.JsObject

/** Something a Viz stores. (Like a "tag")
  *
  * A single Viz (that is, one plugin operating on one document set on behalf
  * of one user) may store arbitrary objects. This class represents a
  * compromise between performance and flexibility.
  *
  * A plugin may:
  *
  * * Index its objects by String or Long (e.g., "find me the tag named 'moo'")
  * * Store arbitrary data for each object (e.g., "name": "my-great-tag")
  * * Store multiple types of objects (by using indexedLong or indexedString to
  *   differentiate them)
  */
case class VizObject(
  id: Long,
  vizId: Long,
  indexedLong: Option[Long],
  indexedString: Option[String],
  json: JsObject
) {
  def update(attributes: VizObject.UpdateAttributes): VizObject = copy(
    indexedLong=attributes.indexedLong,
    indexedString=attributes.indexedString,
    json=attributes.json
  )
}

object VizObject {
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

  def build(id: Long, vizId: Long, attributes: CreateAttributes): VizObject = VizObject(
    id,
    vizId,
    attributes.indexedLong,
    attributes.indexedString,
    attributes.json
  )
}
