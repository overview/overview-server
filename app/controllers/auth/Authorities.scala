package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Future,blocking}
import scala.slick.lifted.{RunnableCompiled}

import models.{OverviewDatabase,OverviewUser}
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.ApiToken

trait Authorities {
  protected def db[A](f: Session => A): A

  private val q = Authorities.queries

  /** Allows any user. */
  def anyUser = new Authority {
    override def apply(user: OverviewUser) = true
    override def apply(apiToken: ApiToken) = Future.successful(true)
  }

  /** Allows only admin users. */
  def adminUser = new Authority {
    override def apply(user: OverviewUser) = user.isAdministrator
    override def apply(apiToken: ApiToken) = Future.successful(false)
  }

  /** Allows any user who is owner of the given DocumentSet ID. */
  def userOwningDocumentSet(id: Long) = new Authority {
    override def apply(user: OverviewUser) = check(q.userDocumentSet(user.email, id))
    override def apply(apiToken: ApiToken) = Future.successful(apiToken.documentSetId == id)
  }

  /** Allows any user who is owner of the DocumentSet associated with the given Tag. */
  def userOwningTag(documentSetId: Long, id: Long) = new Authority {
    override def apply(user: OverviewUser) = check(q.userDocumentSetTag(user.email, documentSetId, id))
    override def apply(apiToken: ApiToken) = (apiToken.documentSetId == documentSetId) match {
      case true => Future(check(q.documentSetTag(documentSetId, id)))
      case false => Future.successful(false)
    }
  }

  /** Allows any user who is owner of the DocumentSet associated with the given Tree ID */
  def userOwningTree(id: Long) = new Authority {
    override def apply(user: OverviewUser) = check(q.userTree(user.email, id))
    override def apply(apiToken: ApiToken) = Future.successful(false)
  }

  /** Allows any user who is owner of the given View */
  def userOwningView(id: Long) = new Authority {
    override def apply(user: OverviewUser) = check(q.userView(user.email, id))
    override def apply(apiToken: ApiToken) = Future(check(q.apiTokenView(apiToken.token, id)))
  }

  /** Allows any user who is owner of the given StoreObject */
  def userOwningStoreObject(id: Long) = new Authority {
    override def apply(user: OverviewUser) = false
    override def apply(apiToken: ApiToken) = Future(check(q.apiTokenStoreObject(apiToken.token, id)))
  }
  
  /** Allows any user who is a viewer of the given DocumentSet ID. */
  def userViewingDocumentSet(id: Long) = userOwningDocumentSet(id)

  /** Allows any user with any role for the given Document ID. */
  def userOwningDocument(id: Long) = new Authority {
    override def apply(user: OverviewUser) = check(q.userDocument(user.email, id))
    override def apply(apiToken: ApiToken) = Future(check(q.documentSetDocument(apiToken.documentSetId, id)))
  }

  def userOwningJob(id: Long) = new Authority {
    override def apply(user: OverviewUser) = check(q.userJob(user.email, id))
    override def apply(apiToken: ApiToken) = Future.successful(false)
  }

  private def check(f: RunnableCompiled[Query[Column[Boolean],Boolean,Seq],Seq[Boolean]]): Boolean = {
    db { session => f.firstOption(session).getOrElse(false) }
  }
}

object Authorities extends Authorities {
  override protected def db[A](f: Session => A) = blocking(OverviewDatabase.withSlickSession(f))

  /** A bunch of queries that return true if successful and no rows otherwise. */
  private object queries {
    import org.overviewproject.models.tables._

    lazy val userDocumentSetTag = Compiled { (email: Column[String], documentSetId: Column[Long], tagId: Column[Long]) =>
      for {
        tag <- Tags if tag.id === tagId && tag.documentSetId === documentSetId
        dsu <- DocumentSetUsers if dsu.documentSetId === tag.documentSetId && dsu.userEmail === email
      } yield (true)
    }

    lazy val documentSetTag = Compiled { (documentSetId: Column[Long], tagId: Column[Long]) =>
      Tags
        .filter(_.id === tagId)
        .filter(_.documentSetId === documentSetId)
        .map(_ => (true))
    }

    lazy val userTree = Compiled { (email: Column[String], treeId: Column[Long]) =>
      for {
        dsu <- DocumentSetUsers if dsu.userEmail === email
        tree <- Trees if tree.id === treeId && tree.documentSetId === dsu.documentSetId
      } yield (true)
    }

    lazy val userView = Compiled { (email: Column[String], viewId: Column[Long]) =>
      for {
        view <- Views if view.id === viewId
        dsu <- DocumentSetUsers if dsu.documentSetId === view.documentSetId && dsu.userEmail === email
      } yield (true)
    }

    lazy val apiTokenView = Compiled { (apiToken: Column[String], viewId: Column[Long]) =>
      Views
        .filter(_.id === viewId)
        .filter(_.apiToken === apiToken)
        .map(_ => (true))
    }

    lazy val apiTokenStoreObject = Compiled { (token: Column[String], objectId: Column[Long]) =>
      for {
        obj <- StoreObjects if obj.id === objectId
        store <- Stores if store.id === obj.storeId && store.apiToken === token
      } yield (true)
    }

    lazy val userDocument = Compiled { (email: Column[String], documentId: Column[Long]) =>
      for {
        document <- Documents if document.id === documentId
        dsu <- DocumentSetUsers if dsu.documentSetId === document.documentSetId && dsu.userEmail === email
      } yield (true)
    }

    lazy val userDocumentSet = Compiled { (email: Column[String], documentSetId: Column[Long]) =>
      DocumentSetUsers
        .filter(_.userEmail === email)
        .filter(_.documentSetId === documentSetId)
        .map(_ => (true))
    }

    lazy val documentSetDocument = Compiled { (documentSetId: Column[Long], documentId: Column[Long]) =>
      Documents
        .filter(_.id === documentId)
        .filter(_.documentSetId === documentSetId)
        .map(_ => (true))
    }

    lazy val userJob = Compiled { (email: Column[String], jobId: Column[Long]) =>
      for {
        job <- DocumentSetCreationJobs if job.id === jobId
        dsu <- DocumentSetUsers if dsu.documentSetId === job.documentSetId && dsu.userEmail === email
      } yield (true)
    }
  }
}
