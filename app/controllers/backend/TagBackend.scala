package controllers.backend

import scala.concurrent.Future
import slick.jdbc.StaticQuery

import org.overviewproject.models.Tag
import org.overviewproject.models.tables.Tags

trait TagBackend {
  /** Lists all Tags for the given DocumentSet. */
  def index(documentSetId: Long): Future[Seq[Tag]]

  /** Returns one Tag, or None if it does not exist. */
  def show(documentSetId: Long, id: Long): Future[Option[Tag]]

  /** Returns a newly-created Tag. */
  def create(documentSetId: Long, attributes: Tag.CreateAttributes): Future[Tag]

  /** Updates a Tag.
    *
    * If the Tag exists, this updates it and returns it. Otherwise, it returns
    * None.
    */
  def update(documentSetId: Long, id: Long, attributes: Tag.UpdateAttributes): Future[Option[Tag]]

  /** Destroys a Tag. */
  def destroy(documentSetId: Long, id: Long): Future[Unit]
}

trait DbTagBackend extends TagBackend with DbBackend {
  import databaseApi._

  lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Rep[Long]) =>
    Tags
      .filter(_.documentSetId === documentSetId)
  }

  lazy val byIdsCompiled = Compiled { (documentSetId: Rep[Long], id: Rep[Long]) =>
    Tags
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === id)
  }

  lazy val attributesByIdsCompiled = Compiled { (documentSetId: Rep[Long], id: Rep[Long]) =>
    Tags
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === id)
      .map(t => (t.name, t.color))
  }

  lazy val inserter = (Tags.map(t => (t.documentSetId, t.name, t.color)) returning Tags)

  override def index(documentSetId: Long) = database.seq(byDocumentSetIdCompiled(documentSetId))

  override def show(documentSetId: Long, id: Long) = database.option(byIdsCompiled(documentSetId, id))

  override def create(documentSetId: Long, attributes: Tag.CreateAttributes) = database.run {
    inserter.+=((documentSetId, attributes.name, attributes.color))
  }

  override def update(documentSetId: Long, id: Long, attributes: Tag.UpdateAttributes) = {
    database.run(attributesByIdsCompiled(documentSetId, id).update((attributes.name, attributes.color)))
      .map(_ match {
        case 0 => None
        case _ => Some(Tag(id, documentSetId, attributes.name, attributes.color))
      })(database.executionContext)
  }

  override def destroy(documentSetId: Long, id: Long) = {
    database.runUnit(sqlu"""
      WITH valid_id AS (
        SELECT id FROM tag WHERE document_set_id = $documentSetId AND id = $id
      )
      , delete_document_tags AS (
        DELETE FROM document_tag WHERE tag_id IN (SELECT id FROM valid_id)
      )
      DELETE FROM tag WHERE id IN (SELECT id FROM valid_id)
    """)
  }
}

object TagBackend extends DbTagBackend with org.overviewproject.database.DatabaseProvider
