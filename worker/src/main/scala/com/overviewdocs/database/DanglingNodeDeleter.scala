package com.overviewdocs.database

import scala.concurrent.{ExecutionContext,Future}

/** Deletes dangling nodes.
  *
  * A dangling node is a node left behind by a crashed worker. If it exists
  * while the reclustering worker isn't running, it's safe to delete the node
  * and all associated children, to reclaim disk space.
  *
  * If the worker _is_ running, *do not* run this code: it will wreak havoc on
  * the in-progress job.
  */
object DanglingNodeDeleter extends HasDatabase {
  def run(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    database.runUnit(sqlu"""
      WITH node_ids AS (SELECT id FROM node WHERE root_id IN (SELECT root_node_id FROM dangling_node))
      , delete1 AS (DELETE FROM node_document WHERE node_id IN (SELECT id FROM node_ids))
      , delete2 AS (DELETE FROM dangling_node)
      DELETE FROM node WHERE id IN (SELECT id FROM node_ids)
    """)
  }
}
