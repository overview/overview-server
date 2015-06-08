package controllers.backend

import scala.concurrent.Future

import org.overviewproject.database.DatabaseProvider
import org.overviewproject.models.{ApiToken,DocumentSet,DocumentSetUser,View}
import org.overviewproject.models.tables.{ApiTokens,DocumentSetUsers,DocumentSets,Plugins,Views}

trait DocumentSetBackend {
  /** Creates a DocumentSet, a DocumentSetUser, and one View per autocreate Plugin.
    *
    * Throws an error if something unfathomable happens.
    */
  def create(attributes: DocumentSet.CreateAttributes, userEmail: String): Future[DocumentSet]

  /** Returns a single DocumentSet. */
  def show(documentSetId: Long): Future[Option[DocumentSet]]

  /** Returns the number of document sets owned or viewed by the given user. */
  def countByUserEmail(userEmail: String): Future[Int]
}

trait DbDocumentSetBackend extends DocumentSetBackend with DbBackend {
  import databaseApi._
  private implicit val ec = database.executionContext

  override def create(attributes: DocumentSet.CreateAttributes, userEmail: String) = {
    database.seq(autocreatePlugins).flatMap { plugins =>
      val queries = for {
        documentSet <- documentSetInserter.+=(attributes)

        _ <- documentSetUserInserter.+=(DocumentSetUser(documentSet.id, userEmail, DocumentSetUser.Role(true)))

        apiTokens <- apiTokenInserter.++=(plugins.map { plugin =>
          ApiToken.generate(userEmail, Some(documentSet.id), "[plugin-autocreate] " + plugin.name)
        })

        _ <- viewInserter.++=(plugins.zip(apiTokens).map { case (plugin, apiToken) =>
          (documentSet.id, View.CreateAttributes(plugin.url, apiToken.token, plugin.name))
        })
      } yield documentSet

      database.run(queries.transactionally)
    }
  }

  override def show(documentSetId: Long) = database.option(byIdCompiled(documentSetId))

  override def countByUserEmail(userEmail: String) = {
    database.run(countByUserEmailCompiled(userEmail).result)
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

  private lazy val countByUserEmailCompiled = Compiled { (userEmail: Rep[String]) =>
    DocumentSetUsers
      .filter(_.userEmail === userEmail)
      .length
  }
}

object DocumentSetBackend extends DbDocumentSetBackend with DatabaseProvider
