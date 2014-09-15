package org.overviewproject.models

import java.sql.Timestamp
import java.util.Date
import play.api.libs.json.JsObject
import scala.util.Try

/** A vizualization, served by a plugin.
  *
  * Both Overview and the end user will request things of the plugin, which is
  * a special web server. They will request from `${url}/vizs/${id}/...` and
  * `${url}/document-sets/${documentSetId}/...`, and they'll send the API token
  * in an HTTP header.
  *
  * The Viz's state is stored in `json`. The Viz has full control over it.
  *
  * XXX right now, Overview will present `json` in a tooltip.
  * TODO make the plugin do that instead.
  */
case class Viz(
  id: Long,
  documentSetId: Long,
  url: String,
  apiToken: String,
  title: String,
  createdAt: Timestamp,
  json: JsObject
) {
  def update(attributes: Viz.UpdateAttributes): Viz = copy(
    title=attributes.title,
    json=attributes.json
  )
}

object Viz {
  /** The parts of a Viz the user may set when creating it */
  case class CreateAttributes(
    url: String,
    apiToken: String,
    title: String,
    json: JsObject
  )

  /** The parts of a Viz the user may set when modifying it */
  case class UpdateAttributes(
    title: String,
    json: JsObject
  )

  def build(id: Long, documentSetId: Long, attributes: Viz.CreateAttributes) = Viz(
    id=id,
    documentSetId=documentSetId,
    url=attributes.url,
    apiToken=attributes.apiToken,
    title=attributes.title,
    createdAt=new Timestamp(scala.compat.Platform.currentTime),
    json=attributes.json
  )
}
