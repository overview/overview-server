package org.overviewproject.tree.orm

import org.squeryl.PrimitiveTypeMode._


object Schema extends org.squeryl.Schema {
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)
  
  val nodes = table[Node]
}