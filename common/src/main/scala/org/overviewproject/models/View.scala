package org.overviewproject.models

import java.sql.Timestamp
import java.util.Date

/** An iframe served by a plugin.
  *
  * Both Overview and the end user will request things of the plugin, which is
  * a special web server. They will request from `${url}/store/...` and
  * `${url}/document-sets/${documentSetId}/...`, and they'll send the API token
  * in an HTTP header.
  */
case class View(
  id: Long,
  documentSetId: Long,
  url: String,
  apiToken: String,
  title: String,
  createdAt: Timestamp
) {
  def update(attributes: View.UpdateAttributes): View = copy(
    title=attributes.title
  )
}

object View {
  /** The parts of a View the user may set when creating it */
  case class CreateAttributes(
    url: String,
    apiToken: String,
    title: String
  )

  /** The parts of a View the user may set when modifying it */
  case class UpdateAttributes(
    title: String
  )

  def build(id: Long, documentSetId: Long, attributes: View.CreateAttributes) = View(
    id=id,
    documentSetId=documentSetId,
    url=attributes.url,
    apiToken=attributes.apiToken,
    title=attributes.title,
    createdAt=new Timestamp(scala.compat.Platform.currentTime)
  )
}
