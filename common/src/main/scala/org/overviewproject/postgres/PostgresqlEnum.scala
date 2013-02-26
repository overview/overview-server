package org.overviewproject.postgres

import org.squeryl.customtypes.StringField

case class PostgresqlEnum(v: String, val typeName: String) extends StringField(v) {
  
  override def canEqual(other: Any): Boolean = other.isInstanceOf[PostgresqlEnum]

}
