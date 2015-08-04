package com.overviewdocs.background.filecleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import com.overviewdocs.models.tables.Files
import com.overviewdocs.database.HasDatabase

/**
 * Looks for [[File]]s with `referenceCount == 0`
 */
trait DeletedFileFinder extends HasDatabase {
  import database.api._
  
  def deletedFileIds: Future[Iterable[Long]] = database.seq(Files.filter(_.referenceCount === 0).map(_.id))
}

object DeletedFileFinder extends DeletedFileFinder
