package controllers.backend

import org.overviewproject.models.DocumentTag
import org.overviewproject.models.tables.DocumentTags

class DbDocumentTagBackendSpec extends DbBackendSpecification {
  trait BaseScope extends DbScope {
    val backend = new DbDocumentTagBackend with org.overviewproject.database.DatabaseProvider
  }

  "DbDocumentTagBackend" should {
    "#indexMany" should {
      trait IndexManyScope extends BaseScope {
        val documentSet = factory.documentSet()
        val doc1 = factory.document(documentSetId=documentSet.id)
        val doc2 = factory.document(documentSetId=documentSet.id)
        val doc3 = factory.document(documentSetId=documentSet.id) // no joins
        val tag1 = factory.tag(documentSetId=documentSet.id)
        val tag2 = factory.tag(documentSetId=documentSet.id)
        val tag3 = factory.tag(documentSetId=documentSet.id) // no joins
        factory.documentTag(documentId=doc1.id, tagId=tag1.id)
        factory.documentTag(documentId=doc2.id, tagId=tag1.id)
        factory.documentTag(documentId=doc1.id, tagId=tag2.id)

        lazy val result: Map[Long,Seq[Long]] = await(backend.indexMany(Seq(doc1.id, doc2.id, doc3.id)))
      }

      "return Tag IDs for Documents that have them" in new IndexManyScope {
        result(doc1.id) must containTheSameElementsAs(Seq(tag1.id, tag2.id))
        result(doc2.id) must beEqualTo(Seq(tag1.id))
      }

      "return nothing for Documents that have no Tags" in new IndexManyScope {
        result.isDefinedAt(doc3.id) must beFalse
      }

      "not return Documents that were not requested" in new IndexManyScope {
        val doc4 = factory.document(documentSetId=documentSet.id)
        factory.documentTag(documentId=doc4.id, tagId=tag1.id)
        result.isDefinedAt(doc4.id) must beFalse
      }

      "work with an empty set of documents" in new IndexManyScope {
        await(backend.indexMany(Seq())) must beEqualTo(Map())
      }
    }
  }
}
