package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSetComponent
import org.squeryl.{ Query, Table }
import org.squeryl.dsl.ast.LogicalBoolean

class DocumentSetRelationFinder[A, B](
    relationTable: Table[A], documentSetComponentTable: Table[B]) extends Finder {

  def relationByDocumentSetComponent(
    documentSetIdInComponent: B => LogicalBoolean, idInComponent: B => Long, idInRelation: A => Long): Query[A] = {

    val idsRelatedToDocumentSet = from(documentSetComponentTable)(c =>
      where(documentSetIdInComponent(c))
        select (idInComponent(c)))

    relationTable.where(idInRelation(_) in idsRelatedToDocumentSet)

  }


}