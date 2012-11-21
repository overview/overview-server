package models

import org.specs2.mutable.Specification

class PersistentDocumentListParserSpec extends Specification {

  "PersisteDocumentListParser" should {

    "return empty list given empty input" in {
      val persistentDocumentListParser = new DocumentListParser()

      val documents = persistentDocumentListParser.createDocuments(Nil, Nil, Nil)

      documents must be empty
    }

    "create documents from data" in {
      val documentData = List((10l, "title1", Some("documentCloudId1")),
	(20l, "title2", Some("documentCloudId2")),
        (30l, "title3", Some("documentCloudId3")))
      val documentTagData = List((10l, 15l), (20l, 5l))
      val documentNodeData = List((10l, 22l), (10l, 44l), (20l, 33l), (30l, 33l))
      val persistentDocumentListParser = new DocumentListParser()

      val documents = persistentDocumentListParser.createDocuments(documentData,
        documentTagData, documentNodeData)

      val ids = documents.map(_.id)
      val titles = documents.map(_.title)
      val documentCloudIds = documents.map(_.documentCloudId)
      val tags = documents.map(_.tags)
      val nodes = documents.map(_.nodes)

      ids must haveTheSameElementsAs(List(10l, 20l, 30l))
      titles must haveTheSameElementsAs(List("title1", "title2", "title3"))
      documentCloudIds must haveTheSameElementsAs(List(Some("documentCloudId1"), Some("documentCloudId2"), Some("documentCloudId3")))
      tags must haveTheSameElementsAs(List(Seq(5l), Seq(15l), Seq()))
      nodes must haveTheSameElementsAs(List(Seq(22l, 44l), Seq(33l), Seq(33l)))
    }
  }
}
