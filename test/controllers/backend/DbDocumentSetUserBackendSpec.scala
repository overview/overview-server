package controllers.backend

import org.overviewproject.models.DocumentSetUser
import org.overviewproject.models.DocumentSetUser.Role
import org.overviewproject.models.tables.DocumentSetUsers

class DbDocumentSetUserBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbDocumentSetUserBackend with org.overviewproject.database.DatabaseProvider

    def find(documentSetId: Long, userEmail: String): Option[DocumentSetUser] = {
      import blockingDatabaseApi._
      blockingDatabase.option(
        DocumentSetUsers
          .filter(_.documentSetId === documentSetId)
          .filter(_.userEmail === userEmail)
      )
    }

    def findAll(documentSetId: Long): Seq[DocumentSetUser] = {
      import blockingDatabaseApi._
      blockingDatabase.seq(
        DocumentSetUsers.filter(_.documentSetId === documentSetId)
      )
    }
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      val documentSet = factory.documentSet()
      val documentSetId = documentSet.id
      lazy val ret = await(backend.index(documentSetId))
    }

    "not list owners" in new IndexScope {
      factory.documentSetUser(documentSetId, "user@example.org", Role(true))
      ret must beEqualTo(Seq())
    }

    "list viewers in alphabetical order" in new IndexScope {
      val z = factory.documentSetUser(documentSetId, "z@example.org", Role(false))
      val a = factory.documentSetUser(documentSetId, "a@example.org", Role(false))
      ret must beEqualTo(Seq(a, z))
    }

    "not list viewers in a different document set" in new IndexScope {
      val documentSet2 = factory.documentSet()
      factory.documentSetUser(documentSet2.id, "user@example.org", Role(false))
      ret must beEqualTo(Seq())
    }
  }

  "#createOwner" should {
    trait CreateOwnerScope extends BaseScope {
      val documentSet = factory.documentSet()
      def go(documentSetId: Long, userEmail: String) = await(backend.createOwner(documentSetId, userEmail))
    }

    "create a DocumentSetUser" in new CreateOwnerScope {
      val expect = DocumentSetUser(documentSet.id, "user@example.org", Role(true))
      go(documentSet.id, "user@example.org") must beEqualTo(expect)
      find(documentSet.id, "user@example.org") must beSome(expect)
    }

    "not overwrite a DocumentSetUser" in new CreateOwnerScope {
      val existing = factory.documentSetUser(documentSet.id, "user@example.org", Role(true))
      go(documentSet.id, "user@example.org") must throwA[Exception]
      // PSQLException: : ERROR: current transaction is aborted, commands ignored until end of transaction block
      //find(documentSet.id, "user@example.org") must beSome(existing) // role stays the same
      //findAll(documentSet.id).length must beEqualTo(1) // no extra row added
    }
  }

  "#update" should {
    trait UpdateScope extends BaseScope {
      val documentSet = factory.documentSet()
      def go(documentSetId: Long, userEmail: String) = await(backend.update(documentSetId, userEmail))
    }

    "create a DocumentSetUser" in new UpdateScope {
      val expect = DocumentSetUser(documentSet.id, "user@example.org", Role(false))
      go(documentSet.id, "user@example.org") must beSome(expect)
      find(documentSet.id, "user@example.org") must beSome(expect)
    }

    "not overwrite a DocumentSetUser" in new UpdateScope {
      val existing = factory.documentSetUser(documentSet.id, "user@example.org", Role(true))
      go(documentSet.id, "user@example.org") must beNone
      // PSQLException: : ERROR: current transaction is aborted, commands ignored until end of transaction block
      //find(documentSet.id, "user@example.org") must beSome(existing) // role stays the same
      //findAll(documentSet.id).length must beEqualTo(1) // no extra row added
    }
  }

  "#destroy" should {
    trait DestroyScope extends BaseScope {
      val documentSet = factory.documentSet()
      def go(documentSetId: Long, userEmail: String) = await(backend.destroy(documentSetId, userEmail))
    }

    "delete a DocumentSetUser" in new DestroyScope {
      factory.documentSetUser(documentSet.id, "user@example.org", Role(false))
      go(documentSet.id, "user@example.org")
      find(documentSet.id, "user@example.org") must beNone
    }

    "not delete other DocumentSetUsers" in new DestroyScope {
      factory.documentSetUser(documentSet.id, "user1@example.org", Role(false))
      val existing = factory.documentSetUser(documentSet.id, "user2@example.org", Role(false))
      go(documentSet.id, "user1@example.org")
      find(documentSet.id, "user2@example.org") must beSome(existing)
    }

    "not delete an owner" in new DestroyScope {
      val existing = factory.documentSetUser(documentSet.id, "user@example.org", Role(true))
      go(documentSet.id, "user@example.org")
      find(documentSet.id, "user@example.org") must beSome(existing)
    }

    "succeed if the DocumentSetUser does not exist" in new DestroyScope {
      { go(documentSet.id, "user@example.org") } must not(throwA[Exception])
    }
  }
}
