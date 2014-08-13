package org.overviewproject.models

import java.sql.Timestamp
import java.util.Date
import play.api.libs.json.JsObject
import scala.util.Try

/** TODO remove this; just use Viz.
  *
  * The history: first, there was the Tree class. Then we decided we wanted to
  * think of Tree as a Viz; so we made this trait, and we called it Viz. Then
  * we decided what a Viz actually _was_ ... and a Tree isn't one.
  *
  * But _users_ will think of Trees and Vizs as the similar. We'll list them
  * alongside one another, for instance. So we had to keep the old Viz trait,
  * minus a couple of attributes that made no sense. We renamed it VizLike.
  *
  * We should make Trees similar to Vizs, so we can remove VizLike.
  */
trait VizLike {
  val id: Long
  val documentSetId: Long
  val title: String
  val createdAt: Date

  /** Describes how the Viz was created.
    *
    * For instance, one pair might be ("lang" -&gt; "en"). Both key and value
    * should pass through i18n before being presented to the user.
    */
  def creationData : Iterable[(String,String)]

  def documentCount: Int = Try(creationData.toMap.apply("nDocuments").toInt).getOrElse(-1)
  def jobId: Long = Try(creationData.toMap.apply("jobId").toLong).getOrElse(-1L)
}

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
) extends VizLike {
  override def creationData = json.value.mapValues(_.toString)
}
