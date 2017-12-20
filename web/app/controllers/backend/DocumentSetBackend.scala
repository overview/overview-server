package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.metadata.MetadataSchema
import com.overviewdocs.models.{ApiToken,DocumentSet,DocumentSetUser,View}
import com.overviewdocs.models.tables.{ApiTokens,DocumentSetUsers,DocumentSets,Plugins,Views}
import models.pagination.{Page,PageRequest}
import models.tables.Users

@ImplementedBy(classOf[DbDocumentSetBackend])
trait DocumentSetBackend {
  /** Creates a DocumentSet, a DocumentSetUser, and one View per autocreate Plugin.
    *
    * Throws an error if something unfathomable happens.
    */
  def create(attributes: DocumentSet.CreateAttributes, userEmail: String): Future[DocumentSet]

  /** Sets whether the DocumentSet is public.
    *
    * Ignores a missing DocumentSet.
    */
  def updatePublic(documentSetId: Long, public: Boolean): Future[Unit]

  /** Sets whether the DocumentSet is deleted.
    *
    * Ignores a missing DocumentSet.
    */
  def updateDeleted(documentSetId: Long, deleted: Boolean): Future[Unit]

  /** Sets a new MetadataSchema on a DocumentSet.
    *
    * Ignores a missing DocumentSet.
    */
  def updateMetadataSchema(documentSetId: Long, schema: MetadataSchema): Future[Unit]

  /** Finds a Page of DocumentSets for a User.
    *
    * The DocumentSets will be sorted by createdAt, newest to oldest.
    *
    * DocumentSets for which the User is a Viewer will not be returned.
    */
  def indexPageByOwner(email: String, pageRequest: PageRequest): Future[Page[DocumentSet]]

  /** Finds all DocumentSets viewed by a User, with their owners.
    *
    * The DocumentSets will be sorted by createdAt, newest to oldest.
    *
    * DocumentSets for which the User is an Owner will not be returned.
    */
  def indexByViewerEmail(email: String): Future[immutable.Seq[(DocumentSet,String)]]

  /** Finds all public DocumentSets, with their owners.
    *
    * The DocumentSets will be sorted by createdAt, newest to oldest.
    */
  def indexPublic: Future[immutable.Seq[(DocumentSet,String)]]

  /** Returns a single DocumentSet. */
  def show(documentSetId: Long): Future[Option[DocumentSet]]

  /** Returns the number of document sets owned (not viewed) by the given user. */
  def countByOwnerEmail(userEmail: String): Future[Int]
}

class DbDocumentSetBackend @Inject() (
  val database: Database
) extends DocumentSetBackend with DbBackend {
  import database.api._
  import database.executionContext

  private val Owner = DocumentSetUser.Role(true)

  override def create(attributes: DocumentSet.CreateAttributes, userEmail: String) = {
    database.seq(autocreatePlugins).flatMap { plugins =>
      val queries = for {
        documentSet <- documentSetInserter.+=(attributes)

        _ <- documentSetUserInserter.+=(DocumentSetUser(documentSet.id, userEmail, Owner))

        apiTokens <- apiTokenInserter.++=(plugins.map { plugin =>
          ApiToken.generate(userEmail, Some(documentSet.id), "[plugin-autocreate] " + plugin.name)
        })

        _ <- viewInserter.++=(plugins.zip(apiTokens).map { case (plugin, apiToken) =>
          (documentSet.id, View.CreateAttributes(plugin.url, plugin.serverUrlFromPlugin, apiToken.token, plugin.name))
        })
      } yield documentSet

      database.run(queries.transactionally)
    }
  }

  override def show(documentSetId: Long) = database.option(byIdCompiled(documentSetId))

  override def indexPageByOwner(email: String, pageRequest: PageRequest) = {
    page(
      pageByOwnerCompiled(email, pageRequest.offset, pageRequest.limit),
      countByOwnerCompiled(email),
      pageRequest
    )
  }

  override def indexByViewerEmail(email: String) = {
    database.seq(indexByViewerEmailCompiled(email))
  }

  override def indexPublic = {
    database.seq(indexPublicCompiled)
  }

  override def updatePublic(documentSetId: Long, public: Boolean) = {
    database.runUnit(updatePublicCompiled(documentSetId).update(public))
  }

  override def updateDeleted(documentSetId: Long, deleted: Boolean) = {
    database.runUnit(updateDeletedCompiled(documentSetId).update(deleted))
  }

  override def updateMetadataSchema(documentSetId: Long, metadataSchema: MetadataSchema) = {
    database.runUnit(updateMetadataSchemaCompiled(documentSetId).update(metadataSchema))
  }

  override def countByOwnerEmail(userEmail: String) = {
    database.run(countByOwnerCompiled(userEmail).result)
  }

  protected lazy val apiTokenInserter = (ApiTokens returning ApiTokens)
  protected lazy val documentSetInserter = (DocumentSets.map(_.createAttributes) returning DocumentSets)
  protected lazy val documentSetUserInserter = DocumentSetUsers
  protected lazy val viewInserter = (Views.map((v) => (v.documentSetId, v.createAttributes)) returning Views)

  private lazy val autocreatePlugins = Compiled {
    Plugins
      .filter(_.autocreate === true)
      .sortBy((p) => (p.autocreateOrder, p.id))
  }

  private lazy val byIdCompiled = Compiled { (documentSetId: Rep[Long]) =>
    DocumentSets.filter(_.id === documentSetId)
  }

  private lazy val indexByViewerEmailCompiled = Compiled { (email: Rep[String]) =>
    val ids = DocumentSetUsers
      .filter(dsu => dsu.userEmail === email && dsu.role === DocumentSetUser.Role(false))
      .map(_.documentSetId)

    DocumentSets
      .filter(_.id in ids)
      .join(DocumentSetUsers).on((ds, dsu) => ds.id === dsu.documentSetId && dsu.role === DocumentSetUser.Role(true))
      .map(t => t._1 -> t._2.userEmail)
      .sortBy(_._1.createdAt.desc)
  }

  private lazy val indexPublicCompiled = {
    DocumentSets
      .filter(_.isPublic)
      .join(DocumentSetUsers).on((ds, dsu) => ds.id === dsu.documentSetId && dsu.role === DocumentSetUser.Role(true))
      .join(Users).on((dsAndDsu, users) => dsAndDsu._2.userEmail === users.email)
      .map(t => t._1._1 -> t._1._2.userEmail)
      .sortBy(_._1.createdAt.desc)
  }

  private lazy val updatePublicCompiled = Compiled { (documentSetId: Rep[Long]) =>
    DocumentSets.filter(_.id === documentSetId).map(_.isPublic)
  }

  private lazy val updateDeletedCompiled = Compiled { (documentSetId: Rep[Long]) =>
    DocumentSets.filter(_.id === documentSetId).map(_.deleted)
  }

  private lazy val updateMetadataSchemaCompiled = Compiled { (documentSetId: Rep[Long]) =>
    DocumentSets.filter(_.id === documentSetId).map(_.metadataSchema)
  }

  private def documentSetIdsByOwner(email: Rep[String]) = {
    DocumentSetUsers
      .filter(_.userEmail === email)
      .filter(_.role === Owner)
      .map(_.documentSetId)
  }

  private lazy val pageByOwnerCompiled = Compiled { (email: Rep[String], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
    DocumentSets
      .filter(!_.deleted)
      .filter(_.id in documentSetIdsByOwner(email))
      .sortBy(_.createdAt.desc)
      .drop(offset).take(limit)
  }

  private lazy val countByOwnerCompiled = Compiled { email: Rep[String] =>
    DocumentSets
      .filter(!_.deleted)
      .filter(_.id in documentSetIdsByOwner(email))
      .length
  }
}
