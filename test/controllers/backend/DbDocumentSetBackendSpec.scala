package controllers.backend

import org.overviewproject.models.tables.{ApiTokens,DocumentSetUsers,DocumentSets,Views}
import org.overviewproject.models.{ApiToken,DocumentSet,DocumentSetUser,View}

class DbDocumentSetBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbDocumentSetBackend with org.overviewproject.database.DatabaseProvider

    def findDocumentSet(id: Long): Option[DocumentSet] = {
      import blockingDatabaseApi._
      blockingDatabase.option(DocumentSets.filter(_.id === id))
    }

    def findDocumentSetUser(documentSetId: Long): Option[DocumentSetUser] = {
      import blockingDatabaseApi._
      blockingDatabase.option(DocumentSetUsers.filter(_.documentSetId === documentSetId))
    }

    def findApiTokensAndViews(documentSetId: Long): Seq[(ApiToken,View)] = {
      import blockingDatabaseApi._
      val q = for {
        apiToken <- ApiTokens if apiToken.documentSetId === documentSetId
        view <- Views.sortBy(_.id) if view.apiToken === apiToken.token
      } yield (apiToken, view)
      blockingDatabase.seq(q)
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope

    "create a document set with a title" in new CreateScope {
      val ret = await(backend.create(DocumentSet.CreateAttributes("our-title"), "foo@bar.com"))
      val dbRet = findDocumentSet(ret.id)
      dbRet must beSome(ret)
      ret.title must beEqualTo("our-title")
    }

    "create a DocumentSetUser owner" in new CreateScope {
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("our-title"), "foo@bar.com"))
      val ret = findDocumentSetUser(documentSet.id)
      ret must beSome(DocumentSetUser(documentSet.id, "foo@bar.com", DocumentSetUser.Role(true)))
    }

    "create an ApiToken and View when a Plugin is autocreate" in new CreateScope {
      factory.plugin(name="plugin name", url="http://plugin.url", autocreate=true)
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("title"), "foo@bar.com"))
      val ret = findApiTokensAndViews(documentSet.id)
      ret.length must beEqualTo(1)
      ret.headOption must beSome.like { case (apiToken, view) =>
        apiToken.documentSetId must beSome(documentSet.id)
        view.documentSetId must beEqualTo(documentSet.id)
        view.title must beEqualTo("plugin name")
        view.url must beEqualTo("http://plugin.url")
      }
    }

    "not create an ApiToken and View when a Plugin is not autocreate" in new CreateScope {
      factory.plugin(name="plugin name", url="http://plugin.url", autocreate=false)
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("title"), "foo@bar.com"))
      findApiTokensAndViews(documentSet.id).length must beEqualTo(0)
    }

    "create Views according to autocreateOrder" in new CreateScope {
      factory.plugin(name="plugin1", url="http://plugin1.url", autocreate=true, autocreateOrder=2)
      factory.plugin(name="plugin2", url="http://plugin2.url", autocreate=true, autocreateOrder=1)
      val documentSet = await(backend.create(DocumentSet.CreateAttributes("title"), "foo@bar.com"))
      findApiTokensAndViews(documentSet.id).map(_._2.title) must beEqualTo(Seq("plugin2", "plugin1"))
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val documentSet = factory.documentSet()
      val documentSetId = documentSet.id
      lazy val ret = await(backend.show(documentSetId))
    }

    "show a DocumentSet" in new ShowScope {
      ret must beSome { ds: DocumentSet =>
        ds.id must beEqualTo(documentSet.id)
        ds.title must beEqualTo(documentSet.title)
        // etc
      }
    }

    "not show a DocumentSet that does not exist" in new ShowScope {
      override val documentSetId = documentSet.id + 1L
      ret must beNone
    }
  }

  "#countByUserEmail" should {
    trait CountByUserEmailScope extends BaseScope {
      val userEmail = "user1@example.org"
      lazy val ret = await(backend.countByUserEmail(userEmail))
    }

    "not count other users' DocumentSets" in new CountByUserEmailScope {
      val documentSet = factory.documentSet()
      factory.documentSetUser(documentSet.id, "user2@example.org")
      ret must beEqualTo(0)
    }

    "count the user's DocumentSets" in new CountByUserEmailScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      factory.documentSetUser(documentSet1.id, userEmail)
      factory.documentSetUser(documentSet2.id, userEmail)
      ret must beEqualTo(2)
    }
  }
}
