package com.overviewdocs.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.models.tables.Trees

/** HACK and not thread-safe. Be wary. Or make it friendly. */
object TreeIdGenerator extends HasDatabase {
  import database.api._

  lazy val query = Compiled { documentSetId: Rep[Long] =>
    Trees
      .filter(_.documentSetId === documentSetId)
      .map(_.id)
      .max
  }

  def next(documentSetId: Long): Future[Long] = {
    database.run(query(documentSetId).result).map(_ match {
      case Some(id: Long) => id + 1
      case None => documentSetId << 32
    })
  }
}
