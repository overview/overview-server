package org.overviewproject.database

import scala.slick.driver.PostgresDriver

/** Our database driver.
  *
  * Usage:
  *
  *   import org.overviewproject.database.Slick.simple._
  *   ... do stuff like at http://slick.typesafe.com/doc/2.0.2
  */
object Slick
  extends PostgresDriver
