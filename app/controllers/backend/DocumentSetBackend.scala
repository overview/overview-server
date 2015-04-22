package controllers.backend

import scala.concurrent.Future

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

trait DbDocumentSetBackend extends DocumentSetBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  override def create(attributes: DocumentSet.CreateAttributes, userEmail: String) = {
    val dsi = documentSetInserter
    val dsui = documentSetUserInserter
    val ai = apiTokenInserter
    val vi = viewInserter

    db(session => withTransaction(session) {
      val documentSet = dsi.insert(attributes)(session)

      dsui.insert(DocumentSetUser(documentSet.id, userEmail, DocumentSetUser.Role(true)))(session)

      for (plugin <- autocreatePlugins.list(session)) {
        val apiToken = ApiToken.generate(userEmail, Some(documentSet.id), "[plugin-autocreate] " + plugin.name)

        ai.insert(apiToken)(session)

        val viewAttributes = View.CreateAttributes(
          plugin.url,
          apiToken.token,
          plugin.name
        )
        vi.insert((documentSet.id, viewAttributes))(session)
      }

      documentSet
    })
  }

  override def show(documentSetId: Long) = firstOption(byIdCompiled(documentSetId))

  override def countByUserEmail(userEmail: String) = db { session =>
    import org.overviewproject.database.Slick.simple._
    countByUserEmailCompiled(userEmail).run(session)
  }

  private lazy val apiTokenInserter = ApiTokens.insertInvoker
  private lazy val documentSetInserter = (DocumentSets.map(_.createAttributes) returning DocumentSets).insertInvoker
  private lazy val documentSetUserInserter = DocumentSetUsers.insertInvoker
  private lazy val viewInserter = (Views.map((v) => (v.documentSetId, v.createAttributes)) returning Views).insertInvoker

  private lazy val autocreatePlugins = {
    Plugins
      .filter(_.autocreate === true)
      .sortBy((p) => (p.autocreateOrder, p.id))
  }

  private lazy val byIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    DocumentSets.filter(_.id === documentSetId)
  }

  private lazy val countByUserEmailCompiled = Compiled { (userEmail: Column[String]) =>
    DocumentSetUsers
      .filter(_.userEmail === userEmail)
      .length
  }
}

object DocumentSetBackend extends DbDocumentSetBackend with DbBackend
