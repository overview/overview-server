package controllers.backend

import scala.concurrent.Future
import scala.slick.jdbc.StaticQuery

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

trait DbTagBackend extends TagBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    Tags
      .filter(_.documentSetId === documentSetId)
  }

  lazy val byIdsCompiled = Compiled { (documentSetId: Column[Long], id: Column[Long]) =>
    Tags
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === id)
  }

  lazy val attributesByIdsCompiled = Compiled { (documentSetId: Column[Long], id: Column[Long]) =>
    Tags
      .filter(_.documentSetId === documentSetId)
      .filter(_.id === id)
      .map(t => (t.name, t.color))
  }

  lazy val insertInvoker = (Tags.map(t => (t.documentSetId, t.name, t.color)) returning Tags).insertInvoker

  override def index(documentSetId: Long) = db { session =>
    byDocumentSetIdCompiled(documentSetId).list(session)
  }

  override def show(documentSetId: Long, id: Long) = db { session =>
    byIdsCompiled(documentSetId, id).firstOption(session)
  }

  override def create(documentSetId: Long, attributes: Tag.CreateAttributes) = db { session =>
    insertInvoker.insert((documentSetId, attributes.name, attributes.color))(session)
  }

  override def update(documentSetId: Long, id: Long, attributes: Tag.UpdateAttributes) = db { session =>
    attributesByIdsCompiled(documentSetId, id).update((attributes.name, attributes.color))(session) match {
      case 0 => None
      case _ => Some(Tag(id, documentSetId, attributes.name, attributes.color))
    }
  }

  override def destroy(documentSetId: Long, id: Long) = db { session =>
    val q = s"""
      WITH valid_id AS (
        SELECT id FROM tag WHERE document_set_id = ? AND id = ?
      )
      , delete_document_tags AS (
        DELETE FROM document_tag WHERE tag_id IN (SELECT id FROM valid_id)
      )
      DELETE FROM tag WHERE id IN (SELECT id FROM valid_id)
    """
    val sq = StaticQuery.update[Tuple2[Long,Long]](q)
    sq.apply(Tuple2(documentSetId, id)).execute(session)
  }
}

object TagBackend extends DbTagBackend with DbBackend
