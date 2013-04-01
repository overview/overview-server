package org.overviewproject.postgres

import java.sql.ResultSet
import org.squeryl.PrimitiveTypeMode
import org.squeryl.internals.Utils
import org.squeryl.dsl.{ JdbcMapper, TypedExpressionFactory, TEnumValue, TOptionEnumValue, DeOptionizer }
import scala.language.implicitConversions

import org.overviewproject.tree.{ Role, Ownership }

object SquerylEntrypoint extends PrimitiveTypeMode {
}
