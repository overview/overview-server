package models.orm.finders

import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import models.orm.Schema

object DocumentFinder extends Finder {
  /** @return All `Document`s with the given DocumentSet and ID.
    *
    * This can have 0 or 1 row.
    */
  def byId(id: Long) : FinderResult[Document] = {
    Schema.documents.where(_.id === id)
  }
}
