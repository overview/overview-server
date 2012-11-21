package models.orm

import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.internals.{DBType,FieldMetaData,StatementWriter}

class SquerylPostgreSqlAdapter extends PostgreSqlAdapter {
  override def createSequenceName(fmd: FieldMetaData) =
    fmd.parentMetaData.viewOrTable.name + "_" + fmd.columnName + "_seq"

  // Handles Postgres ENUM types properly
  // https://groups.google.com/forum/?fromgroups=#!topic/squeryl/pTXgPe8pQIs
  override protected def writeValue(o: AnyRef, fmd: FieldMetaData, sw: StatementWriter): String = {
    val v = fmd.get(o)

    if (sw.isForDisplay) {
      if (v != null)
        v.toString
      else
        "null"
    } else {
      sw.addParam(convertToJdbcValue(v))

      if (fmd.isCustomType && v.isInstanceOf[PostgresqlEnum]) {
        "? :: " + v.asInstanceOf[PostgresqlEnum].typeName
      } else {
        "?"
      }
    }
  }
}
