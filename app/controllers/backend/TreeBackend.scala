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

trait DbTreeBackend extends TreeBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byIdCompiled = Compiled { (id: Column[Long]) => Trees.filter(_.id === id) }
  lazy val attributesByIdCompiled = Compiled { (id: Column[Long]) =>
    for (t <- Trees if t.id === id) yield (t.title)
  }
  override def update(id: Long, attributes: Tree.UpdateAttributes) = db { session =>
    val count = attributesByIdCompiled(id).update(attributes.title)(session)
    if (count > 0) byIdCompiled(id).firstOption(session) else None
  }

  override def destroy(id: Long) = db { session =>
    import scala.slick.jdbc.StaticQuery.interpolation

    /*
     * We run three DELETEs in a single query, to simulate a transaction and
     * avoid round trips.
     */
    val q = sqlu"""
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
    """
    q.execute(session)
  }
}

object TreeBackend extends DbTreeBackend with DbBackend
