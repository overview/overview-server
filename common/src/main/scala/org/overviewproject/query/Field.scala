package org.overviewproject.query

sealed trait Field
object Field {
  case object All extends Field
  case object Text extends Field
  case object Title extends Field
}
