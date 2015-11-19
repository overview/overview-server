package controllers.backend

import play.api.libs.json.JsObject
import scala.concurrent.Future

import com.overviewdocs.models.FileGroup
import com.overviewdocs.models.tables.{FileGroups,GroupedFileUploads}

trait FileGroupBackend extends Backend {
  /** Changes a FileGroup from a place where the user uploads to a place where
    * the worker processes.
    *
    * Scans for GroupedFileUploads to set nFiles and nBytes.
    *
    * Returns the updated FileGroup, or None if the FileGroup does not exist.
    *
    * Returns an error if the database write fails.
    */
  def addToDocumentSet(
    id: Long,
    documentSetId: Long,
    lang: String,
    splitDocuments: Boolean,
    metadataJson: JsObject
  ): Future[Option[FileGroup]]

  /** Finds or creates a FileGroup.
    *
    * The FileGroup will *not* have an `addToDocumentSetId`. If no FileGroup
    * exists which has no `addToDocumentSetId` with the given parameters, a new
    * one will be created.
    *
    * XXX Unfortunately, this method contains a race. Pray the same user doesn't
    * call it twice simultaneously.
    */
  def findOrCreate(attributes: FileGroup.CreateAttributes): Future[FileGroup]

  /** Finds a FileGroup.
    *
    * The FileGroup *may* have an `addToDocumentSetId`, but it will *not* have
    * `deleted==true`.
    */
  def find(id: Long): Future[Option[FileGroup]]

  /** Finds a FileGroup.
    *
    * The FileGroup will *not* have an `addToDocumentSetId`.
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
  import database.api._
  import database.executionContext

  lazy val byIdCompiled = Compiled { (id: Rep[Long]) =>
    FileGroups
      .filter(_.id === id)
      .filter(_.deleted === false)
  }

  lazy val incompleteByAttributesCompiled = Compiled { (userEmail: Rep[String], apiToken: Rep[Option[String]]) =>
    // Option[String] equality is weird because (None === None) is false.
    // https://github.com/slick/slick/issues/947
    FileGroups
      .filter(_.userEmail === userEmail)
      .filter(fg => (fg.apiToken.isEmpty && apiToken.isEmpty) || (fg.apiToken.isDefined && fg.apiToken === apiToken))
      .filter(_.addToDocumentSetId.isEmpty)
      .filter(_.deleted === false)
  }

  lazy val inserter = (FileGroups.map(g => (g.userEmail, g.apiToken, g.metadataJson, g.deleted)) returning FileGroups)

  lazy val countsByIdCompiled = Compiled { (id: Rep[Long]) =>
    GroupedFileUploads
      .filter(_.fileGroupId === id)
      .groupBy(_.fileGroupId)
      .map { group => (group._2.length, group._2.map(_.size).sum.getOrElse(0L)) }
  }

  lazy val addToDocumentSetByIdCompiled = Compiled { (id: Rep[Long]) =>
    FileGroups
      .filter(_.id === id)
      .filter(_.deleted === false)
      .map { g => (g.addToDocumentSetId, g.lang, g.splitDocuments, g.nFiles, g.nBytes, g.nFilesProcessed, g.nBytesProcessed, g.metadataJson) }
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
          case None => inserter.+=((attributes.userEmail, attributes.apiToken, JsObject(Seq()), false))
          case Some(fileGroup) => DBIO.successful(fileGroup)
        })
    }
  }

  override def find(id: Long) = {
    database.option(byIdCompiled(id))
  }

  override def find(userEmail: String, apiToken: Option[String]) = {
    database.option(incompleteByAttributesCompiled(userEmail, apiToken))
  }

  override def addToDocumentSet(
    id: Long,
    documentSetId: Long,
    lang: String,
    splitDocuments: Boolean,
    metadataJson: JsObject
  ) = {
    for {
      counts <- database.option(countsByIdCompiled(id))
      _ <- database.runUnit(addToDocumentSetByIdCompiled(id).update((
        Some(documentSetId),
        Some(lang),
        Some(splitDocuments),
        counts.map(_._1).orElse(Some(0)),
        counts.map(_._2).orElse(Some(0L)),
        Some(0),
        Some(0L),
        metadataJson
      )))
      maybeFileGroup <- database.option(byIdCompiled(id))
    } yield maybeFileGroup
  }

  override def destroy(id: Long) = {
    database.runUnit(updateDeletedByIdCompiled(id).update(true))
  }
}

object FileGroupBackend extends DbFileGroupBackend
