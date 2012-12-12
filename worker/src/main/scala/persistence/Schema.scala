package persistence

import org.overviewproject.postgres.CustomTypes._
import org.overviewproject.tree.orm.{ Document, Node }

object Schema extends org.squeryl.Schema {
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)
  
  val nodes = table[Node]
  val documents = table[Document]
}