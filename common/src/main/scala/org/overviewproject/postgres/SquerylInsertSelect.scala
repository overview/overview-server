package org.overviewproject.postgres

import scala.language.implicitConversions

import org.squeryl.{Query,Session,Table}
import org.squeryl.internals.{DatabaseAdapter,StatementWriter}
import org.squeryl.dsl.ast.QueryExpressionNode

trait SquerylTableInsertSelect {
  class InsertSelectableDba(dba: DatabaseAdapter) {
    def writeInsertSelect[T,T2](table: Table[T], query: Query[T2], sw: StatementWriter) : Unit = {
      sw.write("insert into ")
      sw.write(dba.quoteName(table.prefixedName))
      sw.write(" ")
      dba.writeQuery(query.ast.asInstanceOf[QueryExpressionNode[T2]], sw)
    }
  }

  class InsertSelectableTable[T](table: Table[T]) {
    /** FIXME this is not type-safe. Be sure T2 and T are compatible. */
    def insertSelect[T2](query: Query[T2]) : Int = {
      val dba = Session.currentSession.databaseAdapter
      val sw = new StatementWriter(false, dba)

      dba.writeInsertSelect(table, query, sw)

      dba.executeUpdateAndCloseStatement(Session.currentSession, sw)
    }
  }

  implicit def tableToInsertSelectableTable[T](table: Table[T]) : InsertSelectableTable[T] = new InsertSelectableTable(table)
  implicit def dbaToInsertSelectableDba[T](dba: DatabaseAdapter) : InsertSelectableDba = new InsertSelectableDba(dba)
}

trait SquerylInsertSelect
  extends SquerylTableInsertSelect
