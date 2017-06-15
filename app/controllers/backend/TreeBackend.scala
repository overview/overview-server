package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.database.TreeIdGenerator
import com.overviewdocs.models.{DocumentSet,DocumentSetUser,Tree}
import com.overviewdocs.models.tables.{DocumentSets,DocumentSetUsers,Trees}

@ImplementedBy(classOf[DbTreeBackend])
trait TreeBackend extends Backend {
  /** Creates a Tree.
    */
  def create(attributes: Tree.CreateAttributes): Future[Tree]

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

  /** Lists all trees with progress != 1.0, with their owner emails.
    *
    * A Tree must haave a DocumentSet by definition, but it needn't have an
    * owner. So the email address may be None.
    */
  def indexIncompleteWithDocumentSetAndOwnerEmail: Future[Seq[(Tree,DocumentSet,Option[String])]]
}

class DbTreeBackend @Inject() extends TreeBackend with DbBackend {
  import database.api._

  lazy val inserter = Trees.returning(Trees)
  lazy val byIdCompiled = Compiled { (id: Rep[Long]) => Trees.filter(_.id === id) }
  lazy val attributesByIdCompiled = Compiled { (id: Rep[Long]) =>
    for (t <- Trees if t.id === id) yield (t.title)
  }

  lazy val indexIncompleteComplied = {
    Trees
      .filter(_.progress =!= 1.0)
      .join(DocumentSets)
        .on(_.documentSetId === _.id)
      .joinLeft(DocumentSetUsers)
        .on((t, dsu) => t._1.documentSetId === dsu.documentSetId && dsu.role === DocumentSetUser.Role(true))
      .map(t => (t._1._1, t._1._2, t._2.map(_.userEmail)))
  }

  override def create(attributes: Tree.CreateAttributes) = {
    import scala.concurrent.ExecutionContext.Implicits.global

    for {
      treeId <- TreeIdGenerator.next(attributes.documentSetId)
      tree <- database.run(inserter.+=(attributes.toTreeWithId(treeId)))
    } yield tree
  }

  override def update(id: Long, attributes: Tree.UpdateAttributes) = {
    database.run {
      attributesByIdCompiled(id).update(attributes.title)
        .andThen(byIdCompiled(id).result.headOption)
    }
  }

  override def indexIncompleteWithDocumentSetAndOwnerEmail = {
    database.seq(indexIncompleteComplied)
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
