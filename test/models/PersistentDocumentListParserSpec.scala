package models

import org.overviewproject.test.DbSpecification

class PersistentDocumentListParserSpec extends DbSpecification {

  "PersisteDocumentListParser" should {

    "return empty list given empty input" in {
      val persistentDocumentListParser = new DocumentListParser()

      val documents = persistentDocumentListParser.createDocuments(Nil, Nil, Nil)

      documents must be empty
    }

    "create documents from data" in {
      val documentData = List((10l, "description1", Some("documentCloudId1"), None),
        (20l, "description2", Some("documentCloudId2"), Some("title2")),
        (30l, "description3", Some("documentCloudId3"), Some("title3")))
      val documentTagData = List((10l, 15l), (20l, 5l))
      val documentNodeData = List((10l, 22l), (10l, 44l), (20l, 33l), (30l, 33l))
      val persistentDocumentListParser = new DocumentListParser()

      val documents = persistentDocumentListParser.createDocuments(documentData,
        documentTagData, documentNodeData)

      val data = documents.map(d => (d.id, d.description, d.documentCloudId, d.title))
      val tags = documents.map(_.tags)
      val nodes = documents.map(_.nodes)

      data must haveTheSameElementsAs(documentData)
      tags must haveTheSameElementsAs(List(Seq(5l), Seq(15l), Seq()))
      nodes must haveTheSameElementsAs(List(Seq(22l, 44l), Seq(33l), Seq(33l)))
    }
  }
}
