package models

import org.specs2.mutable.Specification

class PersistentDocumentListParserSpec extends Specification {

  "PersisteDocumentListParser" should {

    "return empty list given empty input" in {
      val persistentDocumentListParser = new DocumentListParser()

      val documents = persistentDocumentListParser.createDocuments(Nil, Nil)

      documents must be empty
    }

    "create documents from data" in {
      val documentData = List((10l, "title1", "documentCloudId1"),
	(20l, "title2", "documentCloudId2"),
        (30l, "title3", "documentCloudId3"))
      val documentTagData = List((10l, 15l), (20l, 5l))
      val persistentDocumentListParser = new DocumentListParser()

      val documents = persistentDocumentListParser.createDocuments(documentData,
        documentTagData)

      val ids = documents.map(_.id)
      val titles = documents.map(_.title)
      val documentCloudIds = documents.map(_.documentCloudId)
      val tags = documents.map(_.tags)

      ids must haveTheSameElementsAs(List(10l, 20l, 30l))
      titles must haveTheSameElementsAs(List("title1", "title2", "title3"))
      documentCloudIds must haveTheSameElementsAs(List("documentCloudId1", "documentCloudId2", "documentCloudId3"))
      tags must haveTheSameElementsAs(List(Seq(5l), Seq(15l), Seq()))
    }
  }
}
