package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import models.{OverviewDatabase,OverviewUser}
import models.orm.finders.{DocumentFinder,TreeFinder}
import org.overviewproject.models.ApiToken

object Authorities {
  /** Allows any user. */
  def anyUser = new Authority {
    override def apply(user: OverviewUser) = true
    override def apply(apiToken: ApiToken) = Future(true)
  }

  /** Allows only admin users. */
  def adminUser = new Authority {
    override def apply(user: OverviewUser) = user.isAdministrator
    override def apply(apiToken: ApiToken) = Future(false)
  }

  /** Allows any user who is owner of the given DocumentSet ID. */
  def userOwningDocumentSet(id: Long) = new Authority {
    override def apply(user: OverviewUser) = user.ownsDocumentSet(id)
    override def apply(apiToken: ApiToken) = Future(apiToken.documentSetId == id)
  }

  /** Allows any user who is owner of the DocumentSet associated with the given Tree ID */
  def userOwningTree(id: Long) = new Authority {
    override def apply(user: OverviewUser) = user.isAllowedTree(id)

    override def apply(apiToken: ApiToken) = Future {
      OverviewDatabase.inTransaction {
        TreeFinder.byId(id)
          .headOption
          .map(_.documentSetId == apiToken.documentSetId)
          .getOrElse(false)
      }
    }
  }

  /** Allows any user who is owner of the given DocumentSet ID and Tree ID  */
  def userOwningDocumentSetAndTree(documentSetId: Long, treeId: Long) = new Authority {
    override def apply(user: OverviewUser) = {
      user.ownsDocumentSet(documentSetId) && user.isAllowedTree(treeId)
    }

    override def apply(apiToken: ApiToken) = {
      userOwningDocumentSet(documentSetId)(apiToken)
        .flatMap(_ match {
          case true => userOwningTree(treeId)(apiToken)
          case false => Future(false)
        })
    }
  }

  /** Allows any user who is owner of the given Viz */
  def userOwningViz(id: Long) = new Authority {
    override def apply(user: OverviewUser) = ???

    override def apply(apiToken: ApiToken) = Future {
      OverviewDatabase.withSlickSession { implicit session =>
        import org.overviewproject.database.Slick.simple._
        import org.overviewproject.models.tables.Vizs
        Vizs
          .where(_.id === id)
          .where(_.apiToken === apiToken.token)
          .length.run == 1
      }
    }
  }
  
  /** Allows any user who is a viewer of the given DocumentSet ID. */
  def userViewingDocumentSet(id: Long) = new Authority {
    override def apply(user: OverviewUser) = user.canViewDocumentSet(id)

    override def apply(apiToken: ApiToken) = userOwningDocumentSet(id)(apiToken)
  }

  /** Allows any user with any role for the given Document ID. */
  def userOwningDocument(id: Long) = new Authority {
    override def apply(user: OverviewUser) = user.isAllowedDocument(id)

    override def apply(apiToken: ApiToken) = Future {
      OverviewDatabase.inTransaction {
        DocumentFinder.byId(id)
          .headOption
          .map(_.documentSetId == apiToken.documentSetId)
          .getOrElse(false)
      }
    }
  }

  def userOwningJob(id: Long) = new Authority {
    override def apply(user: OverviewUser) = user.isAllowedJob(id)
    override def apply(apiToken: ApiToken) = Future(false)
  }
}
