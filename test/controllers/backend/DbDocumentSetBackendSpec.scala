package controllers.backend

import org.overviewproject.models.DocumentSet

class DbDocumentSetBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentSetBackend
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
