package models.orm

import org.squeryl.adapters.PostgreSqlAdapter
import org.squeryl.internals.FieldMetaData

class SquerylPostgreSqlAdapter extends PostgreSqlAdapter {
  override def createSequenceName(fmd: FieldMetaData) =
    fmd.parentMetaData.viewOrTable.name + "_" + fmd.columnName + "_seq"
}
