package com.overviewdocs.clustering

import scala.collection.mutable

import com.overviewdocs.models.Document
import com.overviewdocs.test.DbSpecification

class CatDocumentsSpec extends DbSpecification {
  trait BaseScope extends DbScope {
    def sum(catDocuments: CatDocuments): Seq[Document] = {
      val result = mutable.ArrayBuffer[Document]()
      catDocuments.foreach(result.+=)
      result.toSeq
    }
  }

  "CatDocumentsSpec" should {
    "get documents" in new BaseScope {
      import database.api._

      val documentSet = factory.documentSet()
      factory.document(id=123L, documentSetId=documentSet.id, text="text1")
      factory.document(id=234L, documentSetId=documentSet.id, text="text2")
      blockingDatabase.runUnit(sqlu"""UPDATE document_set SET sorted_document_ids = '{123,234}'""")

      val catDocuments = new CatDocuments(documentSet.id, None)
      catDocuments.length must beEqualTo(2)
      sum(catDocuments).map(_.id) must beEqualTo(Seq(123L, 234L))
      sum(catDocuments).map(_.text) must beEqualTo(Seq("text1", "text2"))
    }

    "fetch in batches" in new BaseScope {
      import database.api._

      val documentSet = factory.documentSet()
      factory.document(id=123L, documentSetId=documentSet.id, text="text1")
      factory.document(id=234L, documentSetId=documentSet.id, text="text2")
      blockingDatabase.runUnit(sqlu"""UPDATE document_set SET sorted_document_ids = '{123,234}'""")

      val catDocuments = new CatDocuments(documentSet.id, None, 1)
      catDocuments.length must beEqualTo(2)
      sum(catDocuments).map(_.id) must beEqualTo(Seq(123L, 234L))
      sum(catDocuments).map(_.text) must beEqualTo(Seq("text1", "text2"))
    }

    "ignore documents from other document sets" in new BaseScope {
      import database.api._

      val documentSet1 = factory.documentSet()
      factory.document(id=1L, documentSetId=documentSet1.id, text="text1")
      factory.document(id=2L, documentSetId=documentSet1.id, text="text2")
      val documentSet2 = factory.documentSet()
      factory.document(id=3L, documentSetId=documentSet2.id, text="text3")
      blockingDatabase.runUnit(sqlu"""UPDATE document_set SET sorted_document_ids = '{1,2}' WHERE id = ${documentSet1.id}""")
      blockingDatabase.runUnit(sqlu"""UPDATE document_set SET sorted_document_ids = '{3}' WHERE id = ${documentSet2.id}""")

      sum(new CatDocuments(documentSet1.id, None)).map(_.id) must beEqualTo(Seq(1L, 2L))
    }

    "filter for documents with the given tag" in new BaseScope {
      import database.api._

      val documentSet = factory.documentSet()
      val tag = factory.tag(documentSetId=documentSet.id)
      val otherTag = factory.tag(documentSetId=documentSet.id)
      factory.document(id=1L, documentSetId=documentSet.id, text="text1")
      factory.document(id=2L, documentSetId=documentSet.id, text="text2")
      factory.document(id=3L, documentSetId=documentSet.id, text="text3")
      blockingDatabase.runUnit(sqlu"""UPDATE document_set SET sorted_document_ids = '{1,2,3}'""")
      factory.documentTag(1L, tag.id)
      factory.documentTag(1L, otherTag.id) // should not cause duplicates
      factory.documentTag(3L, tag.id)

      sum(new CatDocuments(documentSet.id, Some(tag.id))).map(_.id) must beEqualTo(Seq(1L, 3L))
    }
  }
}
