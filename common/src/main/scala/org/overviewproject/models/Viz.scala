package org.overviewproject.models

import java.util.Date

trait Viz {
  val id: Long
  val title: String
  val createdAt: Date

  /** Helps the client track what created this Viz.
    *
    * The client may keep track of a list of Jobs. At a certain point, a Job
    * will disappear and a Viz will appear. If the client was displaying the
    * Job as "selected", it can use this field to decide to automatically
    * display the Viz as "selected".
    */
  val jobId: Long

  /** Describes how the Viz was created.
    *
    * For instance, one pair might be ("lang" -&gt; "en"). Both key and value
    * should pass through i18n before being presented to the user.
    */
  def creationData : Iterable[(String,String)]
}
