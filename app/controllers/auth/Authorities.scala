package controllers.auth

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.{Future,blocking}
import slick.jdbc.JdbcBackend.Session
import slick.lifted.{ConstColumn,Query,RunnableCompiled}

import models.{OverviewDatabase,User}
import org.overviewproject.database.{Database,DatabaseProvider,HasDatabase}
import org.overviewproject.models.{ApiToken,UserRole}

trait Authorities extends HasDatabase {
  private val q = Authorities.queries

  /** Allows any user. */
  def anyUser = new Authority {
    override def apply(user: User) = Future.successful(true)
    override def apply(apiToken: ApiToken) = Future.successful(true)
  }

  /** Allows only admin users. */
  def adminUser = new Authority {
    override def apply(user: User) = Future.successful(user.role == UserRole.Administrator)
    override def apply(apiToken: ApiToken) = Future.successful(false)
  }

  /** Allows API tokens with documentSetId=None. */
  def apiDocumentSetCreator = new Authority {
    override def apply(user: User) = Future.successful(false)
    override def apply(apiToken: ApiToken) = Future.successful(apiToken.documentSetId.isEmpty)
  }

  /** Allows any user who is owner of the given DocumentSet ID. */
  def userOwningDocumentSet(id: Long) = new Authority {
    override def apply(user: User) = check(q.userDocumentSet(user.email, id))
    override def apply(apiToken: ApiToken) = Future.successful(apiToken.documentSetId == Some(id))
  }

  /** Allows any user who is owner of the DocumentSet associated with the given Tag. */
  def userOwningTag(documentSetId: Long, id: Long) = new Authority {
    override def apply(user: User) = check(q.userDocumentSetTag(user.email, documentSetId, id))
    override def apply(apiToken: ApiToken) = (apiToken.documentSetId == Some(documentSetId)) match {
      case true => check(q.documentSetTag(documentSetId, id))
      case false => Future.successful(false)
    }
  }

  /** Allows any user who is owner of the DocumentSet associated with the given Tree ID */
  def userOwningTree(id: Long) = new Authority {
    override def apply(user: User) = check(q.userTree(user.email, id))
    override def apply(apiToken: ApiToken) = Future.successful(false)
  }

  /** Allows any user who is owner of the given View */
  def userOwningView(id: Long) = new Authority {
    override def apply(user: User) = check(q.userView(user.email, id))
    override def apply(apiToken: ApiToken) = check(q.apiTokenView(apiToken.token, id))
  }

  /** Allows any user who is owner of the given StoreObject */
  def userOwningStoreObject(id: Long) = new Authority {
    override def apply(user: User) = Future.successful(false)
    override def apply(apiToken: ApiToken) = check(q.apiTokenStoreObject(apiToken.token, id))
  }
  
  /** Allows any user who is a viewer of the given DocumentSet ID. */
  def userViewingDocumentSet(id: Long) = new Authority {
    override def apply(user: User) = {
      check(q.userDocumentSet(user.email, id))
        .flatMap(_ match {
          case false => check(q.documentSetPublic(id))
          case true => Future.successful(true)
        })
    }
    override def apply(apiToken: ApiToken) = userOwningDocumentSet(id)(apiToken)
  }

  /** Allows any user with any role for the given Document ID. */
  def userOwningDocument(id: Long) = new Authority {
    override def apply(user: User) = check(q.userDocument(user.email, id))
    override def apply(apiToken: ApiToken) = check(q.maybeDocumentSetDocument(apiToken.documentSetId, id))
  }

  def userOwningJob(id: Long) = new Authority {
    override def apply(user: User) = check(q.userJob(user.email, id))
    override def apply(apiToken: ApiToken) = Future.successful(false)
  }

  private def check(f: RunnableCompiled[Query[ConstColumn[Boolean],Boolean,Seq],Seq[Boolean]]): Future[Boolean] = {
    database.option(f)
      .map(_.getOrElse(false))(database.executionContext)
  }
}

object Authorities extends Authorities with DatabaseProvider {
  /** A bunch of queries that return true if successful and no rows otherwise. */
  private object queries {
    import databaseApi._
    import org.overviewproject.models.tables._

    lazy val userDocumentSetTag = Compiled { (email: Rep[String], documentSetId: Rep[Long], tagId: Rep[Long]) =>
      for {
        tag <- Tags if tag.id === tagId && tag.documentSetId === documentSetId
        dsu <- DocumentSetUsers if dsu.documentSetId === tag.documentSetId && dsu.userEmail === email
      } yield (true)
    }

    lazy val documentSetTag = Compiled { (documentSetId: Rep[Long], tagId: Rep[Long]) =>
      Tags
        .filter(_.id === tagId)
        .filter(_.documentSetId === documentSetId)
        .map(_ => (true))
    }

    lazy val userTree = Compiled { (email: Rep[String], treeId: Rep[Long]) =>
      for {
        dsu <- DocumentSetUsers if dsu.userEmail === email
        tree <- Trees if tree.id === treeId && tree.documentSetId === dsu.documentSetId
      } yield (true)
    }

    lazy val userView = Compiled { (email: Rep[String], viewId: Rep[Long]) =>
      for {
        view <- Views if view.id === viewId
        dsu <- DocumentSetUsers if dsu.documentSetId === view.documentSetId && dsu.userEmail === email
      } yield (true)
    }

    lazy val apiTokenView = Compiled { (apiToken: Rep[String], viewId: Rep[Long]) =>
      Views
        .filter(_.id === viewId)
        .filter(_.apiToken === apiToken)
        .map(_ => (true))
    }

    lazy val apiTokenStoreObject = Compiled { (token: Rep[String], objectId: Rep[Long]) =>
      for {
        obj <- StoreObjects if obj.id === objectId
        store <- Stores if store.id === obj.storeId && store.apiToken === token
      } yield (true)
    }

    lazy val userDocument = Compiled { (email: Rep[String], documentId: Rep[Long]) =>
      for {
        document <- Documents if document.id === documentId
        dsu <- DocumentSetUsers if dsu.documentSetId === document.documentSetId && dsu.userEmail === email
      } yield (true)
    }

    lazy val userDocumentSet = Compiled { (email: Rep[String], documentSetId: Rep[Long]) =>
      DocumentSetUsers
        .filter(_.userEmail === email)
        .filter(_.documentSetId === documentSetId)
        .map(_ => (true))
    }

    lazy val documentSetPublic = Compiled { (documentSetId: Rep[Long]) =>
      DocumentSets
        .filter(_.id === documentSetId)
        .filter(_.isPublic === true)
        .map(_ => (true))
    }

    lazy val maybeDocumentSetDocument = Compiled { (maybeDocumentSetId: Rep[Option[Long]], documentId: Rep[Long]) =>
      Documents
        .filter(_.id === documentId)
        .filter(_.documentSetId === maybeDocumentSetId)
        .map(_ => (true))
    }

    lazy val userJob = Compiled { (email: Rep[String], jobId: Rep[Long]) =>
      for {
        job <- DocumentSetCreationJobs if job.id === jobId
        dsu <- DocumentSetUsers if dsu.documentSetId === job.documentSetId && dsu.userEmail === email
      } yield (true)
    }
  }
}
