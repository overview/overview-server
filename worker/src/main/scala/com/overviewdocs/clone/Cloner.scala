package com.overviewdocs.clone

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.CloneJob
import com.overviewdocs.models.tables.CloneJobs
import com.overviewdocs.searchindex.DocumentSetReindexer
import com.overviewdocs.util.AddDocumentsCommon

/** Clones document sets.
  */
trait Cloner extends HasDatabase {
  protected val documentSetReindexer: DocumentSetReindexer

  /** Clones a document set.
    *
    * This will clone one table at a time, until the destination document set
    * (which we assume is empty to begin with) looks as much like the source
    * document set as possible. In particular, we copy:
    *
    * * document
    * * document_processing_error
    * * tag, document_tag
    * * tree, node, document_node
    *
    * (We assume the row in `document_set` was already copied.)
    *
    * We also increment `file` refcounts.
    *
    * After each table, we update progress in the `clone` table. If the user has
    * cancelled the job, we abort the rest of the copying.
    *
    * If the source document set disappears, we end up copying less stuff than
    * otherwise; nothing stops.
    */
  def run(cloneJob: CloneJob): Future[Unit] = {
    runFromStep(cloneJob, cloneJob.stepNumber, cloneJob.cancelled)
  }

  private val Steps: Array[CloneJob => Future[Unit]] = Array(
    cloneDocumentSetFile2s,
    cloneDocuments,
    indexDocuments,
    cloneDocumentProcessingErrors,
    cloneTags,
    cloneTrees
  )

  private def runFromStep(cloneJob: CloneJob, stepNumber: Int, cancelled: Boolean): Future[Unit] = {
    if (stepNumber >= Steps.length || cancelled) {
      finish(cloneJob)
    } else {
      Steps(stepNumber)(cloneJob)
        .flatMap(_ => reportProgressAndCheckCancel(cloneJob, stepNumber + 1))
        .flatMap(cancelled => runFromStep(cloneJob, stepNumber + 1, cancelled))
    }
  }

  private def indexDocuments(cloneJob: CloneJob): Future[Unit] = {
    documentSetReindexer.reindexDocumentSet(cloneJob.destinationDocumentSetId)
  }

  import database.api._

  private lazy val byId = {
    Compiled { cloneJobId: Rep[Int] =>
      CloneJobs.filter(_.id === cloneJobId)
    }
  }

  private def reportProgressAndCheckCancel(
    cloneJob: CloneJob,
    nextStepNumber: Int
  ): Future[Boolean] = {
    database.option(sql"""
      UPDATE clone_job
      SET step_number = $nextStepNumber
      WHERE id = ${cloneJob.id}
      RETURNING cancelled
    """.as[Boolean])
      .map(_ match {
        case Some(false) => false
        case _ => true
      })
  }

  private def finish(cloneJob: CloneJob): Future[Unit] = {
    for {
      _ <- AddDocumentsCommon.afterAddDocuments(cloneJob.destinationDocumentSetId)
      _ <- database.run(byId(cloneJob.id).delete)
    } yield ()
  }

  private def cloneDocumentSetFile2s(cloneJob: CloneJob): Future[Unit] = {
    database.runUnit(sqlu"""
      INSERT INTO document_set_file2 (document_set_id, file2_id)
      SELECT ${cloneJob.destinationDocumentSetId}, file2_id
      FROM document_set_file2
      WHERE document_set_id = ${cloneJob.sourceDocumentSetId}
        AND file2_id NOT IN (
          SELECT file2_id
          FROM document_set_file2
          WHERE document_set_id = ${cloneJob.destinationDocumentSetId}
        )
    """)
  }

  private def cloneDocuments(cloneJob: CloneJob): Future[Unit] = {
    for {
      _ <- AddDocumentsCommon.beforeAddDocuments(cloneJob.destinationDocumentSetId)
      _ <- database.runUnit(sqlu"""
        WITH all_file_ids AS (
          INSERT INTO document (
            id, document_set_id,
            documentcloud_id, text, url, supplied_id, title,
            created_at, file_id, page_id, file2_id, page_number, metadata_json_text, thumbnail_location
          )
          SELECT 
             ${cloneJob.destinationDocumentSetId << 32} | (${0xffffffffL} & id),
             ${cloneJob.destinationDocumentSetId},
             documentcloud_id, text, url, supplied_id, title,
             created_at, file_id, page_id, file2_id, page_number, metadata_json_text, thumbnail_location
          FROM document
          WHERE document_set_id = ${cloneJob.sourceDocumentSetId}
          RETURNING file_id AS id
        )
        , distinct_file_ids AS (SELECT DISTINCT id AS id FROM all_file_ids WHERE id IS NOT NULL)
        , for_update AS (SELECT id FROM file WHERE id IN (SELECT id FROM distinct_file_ids) FOR UPDATE)
        UPDATE file
        SET reference_count = reference_count + 1
        WHERE id IN (SELECT id FROM distinct_file_ids)
      """)
    } yield ()
  }

  private def cloneDocumentProcessingErrors(cloneJob: CloneJob): Future[Unit] = {
    database.runUnit(sqlu"""
      INSERT INTO document_processing_error (
        document_set_id,
        text_url,
        message,
        status_code,
        headers
      )
      SELECT
        ${cloneJob.destinationDocumentSetId},
        text_url,
        message,
        status_code,
        headers
      FROM document_processing_error
      WHERE document_set_id = ${cloneJob.sourceDocumentSetId}
    """)
  }

  private def cloneTags(cloneJob: CloneJob): Future[Unit] = {
    database.runUnit(sqlu"""
      WITH old_and_new AS (
        SELECT id AS old_id, nextval('tag_id_seq'::regclass) AS new_id, name, color
        FROM tag
        WHERE document_set_id = ${cloneJob.sourceDocumentSetId}
      ), x_new_rows AS (
        INSERT INTO tag (id, document_set_id, name, color)
        SELECT new_id, ${cloneJob.destinationDocumentSetId}, name, color
        FROM old_and_new
      )
      INSERT INTO document_tag (document_id, tag_id)
      SELECT
        ${cloneJob.destinationDocumentSetId << 32} | (${0xffffffffL} & old.document_id),
        old_and_new.new_id
      FROM document_tag old
      INNER JOIN old_and_new ON old.tag_id = old_and_new.old_id
    """)
  }

  private def cloneTrees(cloneJob: CloneJob): Future[Unit] = {
    database.runUnit(sqlu"""
      WITH ins1 AS (
        INSERT INTO node (id, root_id, parent_id, description, cached_size, is_leaf)
        SELECT
          (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & id),
          (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & root_id),
          (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & parent_id),
          description,
          cached_size,
          is_leaf
        FROM node
        WHERE id >= (${cloneJob.sourceDocumentSetId} << 32)
          AND id < ((${cloneJob.sourceDocumentSetId} + 1) << 32)
      ), ins2 AS (
        INSERT INTO node_document (node_id, document_id)
        SELECT
          (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & node_id),
          (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & document_id)
        FROM node_document
        WHERE node_id >= (${cloneJob.sourceDocumentSetId} << 32)
          AND node_id < ((${cloneJob.sourceDocumentSetId} + 1) << 32)
      )
      INSERT INTO tree (
        id,
        document_set_id,
        root_node_id,
        title,
        document_count,
        lang,
        supplied_stop_words,
        important_words,
        description,
        created_at,
        tag_id,
        progress,
        progress_description
      )
      SELECT
        (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & id),
        ${cloneJob.destinationDocumentSetId},
        (${cloneJob.destinationDocumentSetId} << 32) | (${0xffffffffL} & root_node_id),
        title,
        document_count,
        lang,
        supplied_stop_words,
        important_words,
        description,
        CLOCK_TIMESTAMP(),
        NULL,
        progress,
        progress_description
      FROM tree
      WHERE document_set_id = ${cloneJob.sourceDocumentSetId}
    """)
  }
}

object Cloner extends Cloner {
  override protected val documentSetReindexer = DocumentSetReindexer
}
