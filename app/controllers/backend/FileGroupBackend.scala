package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.FileGroup
import org.overviewproject.models.tables.FileGroups

trait FileGroupBackend extends Backend {
  /** Updates a FileGroup.
    *
    * The only property you can update is <tt>completed</tt>.
    *
    * Succeeds if the specified FileGroup does not exist.
    *
    * Returns an error if the database write fails.
    */
  def update(id: Long, completed: Boolean): Future[Unit]

  /** Finds or creates a FileGroup.
    *
    * The FileGroup will <em>not</em> be <tt>completed</tt>. If no FileGroup
    * exists which is not <tt>completed</tt> with the given parameters, a new
    * one will be created.
    *
    * XXX Unfortunately, this method contains a race. Pray the same user doesn't
    * call it twice simultaneously.
    */
  def findOrCreate(attributes: FileGroup.CreateAttributes): Future[FileGroup]

  /** Finds a FileGroup.
    *
    * The FileGroup will <em>not</em> be <tt>completed</tt>.
    */
  def find(userEmail: String, apiToken: Option[String]): Future[Option[FileGroup]]

  /** Queues destruction of an in-progress FileGroup and all associated uploads.
    *
    * When this method returns, the specified file group is invisible. In the
    * future, a backend worker will free up the disk space.
    *
    * Succeeds when the specified FileGroup does not exist.
    */
  def destroy(id: Long): Future[Unit]
}

trait DbFileGroupBackend extends FileGroupBackend with DbBackend {
  import databaseApi._

  lazy val incompleteByAttributesCompiled = Compiled { (userEmail: Rep[String], apiToken: Rep[Option[String]]) =>
    // Option[String] equality is weird because (None === None) is false.
    // https://github.com/slick/slick/issues/947
    FileGroups
      .filter(_.userEmail === userEmail)
      .filter(fg => (fg.apiToken.isEmpty && apiToken.isEmpty) || (fg.apiToken.isDefined && fg.apiToken === apiToken))
      .filter(_.completed === false)
      .filter(_.deleted === false)
  }

  lazy val inserter = (FileGroups.map(g => (g.userEmail, g.apiToken, g.completed, g.deleted)) returning FileGroups)

  lazy val updateCompletedByIdCompiled = Compiled { (id: Rep[Long]) =>
    FileGroups
      .filter(_.id === id)
      .filter(_.deleted === false)
      .map(_.completed)
  }

  lazy val updateDeletedByIdCompiled = Compiled { (id: Rep[Long]) =>
    FileGroups
      .filter(_.id === id)
      .filter(_.deleted === false)
      .map(_.deleted)
  }

  override def findOrCreate(attributes: FileGroup.CreateAttributes) = {
    database.run {
      incompleteByAttributesCompiled(attributes.userEmail, attributes.apiToken).result.headOption
        .flatMap(_ match {
          case None => inserter.+=((attributes.userEmail, attributes.apiToken, false, false))
          case Some(fileGroup) => DBIO.successful(fileGroup)
        })(database.executionContext)
    }
  }

  override def find(userEmail: String, apiToken: Option[String]) = {
    database.option(incompleteByAttributesCompiled(userEmail, apiToken))
  }

  override def update(id: Long, completed: Boolean) = {
    database.runUnit(updateCompletedByIdCompiled(id).update(completed))
  }

  override def destroy(id: Long) = {
    database.runUnit(updateDeletedByIdCompiled(id).update(true))
  }
}

object FileGroupBackend extends DbFileGroupBackend with org.overviewproject.database.DatabaseProvider
