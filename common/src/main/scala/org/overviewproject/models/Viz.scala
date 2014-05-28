package org.overviewproject.models

import java.util.Date

trait Viz {
  val id: Long
  val title: String
  val createdAt: Date

  /** Describes how the Viz was created.
    *
    * For instance, one pair might be ("lang" -&gt; "en"). Both key and value
    * should pass through i18n before being presented to the user.
    */
  def creationData : Iterable[(String,String)]
}
