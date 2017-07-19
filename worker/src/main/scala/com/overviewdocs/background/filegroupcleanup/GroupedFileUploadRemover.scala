package com.overviewdocs.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.GroupedFileUploads


/**
 * Delete [[GroupedFileUpload]]s and their contents.
 */
trait GroupedFileUploadRemover extends HasDatabase {
  import database.api._

  private val DeleteManyChunkSize = 250

  def removeFileGroupUploads(fileGroupId: Long): Future[Unit] = {
    for {
      c <- deleteContents(fileGroupId)
      g <- deleteGroupedFileUploads(fileGroupId)  
    } yield ()
  }

  private def uploadQuery(fileGroupId: Long) = GroupedFileUploads.filter(_.fileGroupId === fileGroupId)

  private def deleteContents(fileGroupId: Long): Future[Unit] = {
    findContentOids(fileGroupId).flatMap(deleteLargeObjectsByOids _)
  }

  protected def deleteLargeObjectsByOids(oids: Seq[Long]): Future[Unit] = {
    // LO.delete() puts the connection in an inconsistent state if the loid is
    // invalid. So we can't use LO.delete() ... or lo_unlink() even. We need
    // some nifty SQL.

    val groups: List[Seq[Long]] = oids.grouped(DeleteManyChunkSize).toList

    def deleteSome(loids: Seq[Long]): Future[Unit] = database.runUnit(sqlu"""
      DO $$$$
      DECLARE
        loids BIGINT[] := ARRAY[#${loids.mkString(",")}];
        loid BIGINT;
      BEGIN
        FOREACH loid IN ARRAY loids LOOP
          BEGIN
            PERFORM lo_unlink(loid);
          EXCEPTION
            WHEN undefined_object THEN NULL;
          END;
        END LOOP;
      END$$$$;
    """)

    def continue(remainingGroups: List[Seq[Long]]): Future[Unit] = remainingGroups match {
      case List() => Future.unit
      case someOids :: moreGroups => for {
        _ <- deleteSome(someOids)
        _ <- continue(moreGroups)
      } yield ()
    }

    continue(groups)
  }

  private def findContentOids(fileGroupId: Long): Future[Seq[Long]] = {
    database.seq(uploadQuery(fileGroupId).map(_.contentsOid))
  }

  private def deleteGroupedFileUploads(fileGroupId: Long): Future[Unit] = {
    database.delete(uploadQuery(fileGroupId))
  }
}

object GroupedFileUploadRemover {
  def apply(): GroupedFileUploadRemover = new GroupedFileUploadRemover {}
}
