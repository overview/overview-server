package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.Tree
import org.overviewproject.models.tables.Trees

trait TreeBackend extends Backend {
  /** Updates a Tree.
    *
    * Returns None if the Tree does not exist; otherwise, returns the updated
    * Tree.
    */
  def update(id: Long, attributes: Tree.UpdateAttributes): Future[Option[Tree]]

  /** Deletes a Tree and all its Nodes.
    *
    * This is a no-op if the Tree does not exist.
    */
  def destroy(id: Long): Future[Unit]
}

trait DbTreeBackend extends TreeBackend with DbBackend {
  import databaseApi._

  lazy val byIdCompiled = Compiled { (id: Rep[Long]) => Trees.filter(_.id === id) }
  lazy val attributesByIdCompiled = Compiled { (id: Rep[Long]) =>
    for (t <- Trees if t.id === id) yield (t.title)
  }

  override def update(id: Long, attributes: Tree.UpdateAttributes) = {
    database.run {
      attributesByIdCompiled(id).update(attributes.title)
        .andThen(byIdCompiled(id).result.headOption)
    }
  }

  override def destroy(id: Long) = {
    /*
     * We run three DELETEs in a single query, to simulate a transaction and
     * avoid round trips.
     */
    database.runUnit(sqlu"""
      WITH root_node_ids AS (
        SELECT root_node_id AS id
        FROM tree
        WHERE id = $id
      ), node_ids AS (
        SELECT id
        FROM node
        WHERE root_id IN (SELECT id FROM root_node_ids)
      ), subdelete1 AS (
        DELETE FROM node_document
        WHERE node_id IN (SELECT id FROM node_ids)
      ), subdelete2 AS (
        DELETE FROM node
        WHERE root_id IN (SELECT id FROM root_node_ids)
      )
      DELETE
      FROM tree
      WHERE id = $id
    """)
  }
}

object TreeBackend extends DbTreeBackend with org.overviewproject.database.DatabaseProvider
