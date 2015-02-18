package org.overviewproject.database

import com.github.tminglei.slickpg._
import scala.slick.driver.PostgresDriver

trait MyPostgresDriver extends PostgresDriver
  with PgArraySupport
{
  override lazy val Implicit = new ImplicitsPlus {}
  override val simple = new SimpleQLPlus {}

  trait ImplicitsPlus extends Implicits with ArrayImplicits with SimpleArrayPlainImplicits
  trait SimpleQLPlus extends SimpleQL with ImplicitsPlus
}

/** Our database driver.
  *
  * Usage:
  *
  *   import org.overviewproject.database.Slick.simple._
  *   ... do stuff like at http://slick.typesafe.com/doc/2.0.2
  */
object Slick extends MyPostgresDriver
