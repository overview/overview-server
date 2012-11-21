package models.orm

import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.internals.{DBType,FieldMetaData,StatementWriter}

class SquerylPostgreSqlAdapter extends PostgreSqlAdapter {
  override def createSequenceName(fmd: FieldMetaData) =
    fmd.parentMetaData.viewOrTable.name + "_" + fmd.columnName + "_seq"

  // Handles Postgres ENUM types properly
  // https://groups.google.com/forum/?fromgroups=#!topic/squeryl/pTXgPe8pQIs
  override protected def writeValue(o: AnyRef, fmd: FieldMetaData, sw: StatementWriter): String = {
    if (sw.isForDisplay) {
      val v = fmd.get(o)
      if (v != null)
        v.toString
      else
        "null"
    } else {
      sw.addParam(convertToJdbcValue(fmd.get(o)))

      val enumtype = fmd.columnAttributes.find {
        case DBType(t) if t.endsWith("_type") => true
        case _ => false
      }
      enumtype.map({
          case DBType(t) => "? ::" + t
      }).getOrElse("?")
    }
  }
}
