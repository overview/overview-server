package controllers.backend

import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.searchindex.{InMemoryIndexClient,IndexClient}

class DbDocumentBackendSpec extends DbBackendSpecification {

  trait BaseScope extends DbScope {
    val testIndexClient: InMemoryIndexClient = InMemoryIndexClient()
    val backend = new TestDbBackend(session) with DbDocumentBackend {
      override val indexClient: IndexClient = testIndexClient
    }

    override def after = {
      testIndexClient.close
      super.after
    }
  }

  "DbDocumentBackendSpec" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id, title=Some("c"), text=Some("foo bar baz"))
        val doc2 = factory.document(documentSetId=documentSet.id, title=Some("a"), text=Some("moo mar maz"))
        val doc3 = factory.document(documentSetId=documentSet.id, title=Some("b"), text=Some("noo nar naz"))
        val documents = Seq(doc1, doc2, doc3)

        await(
          testIndexClient.addDocumentSet(documentSet.id)
            .flatMap(Unit => testIndexClient.addDocuments(documents))
            .flatMap(Unit => testIndexClient.refresh())
        )

        val q = ""

        lazy val ret = await(backend.index(documentSet.id, q=q))
      }

      "show all documents by default" in new IndexScope {
        ret.length must beEqualTo(3)
      }

      "sort documents by title" in new IndexScope {
        ret.map(_.id) must beEqualTo(Seq(doc2.id, doc3.id, doc1.id))
      }

      "search by q" in new IndexScope {
        override val q = "moo"
        ret.map(_.id) must beEqualTo(Seq(doc2.id))
      }
    }
  }
}
