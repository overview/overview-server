package models

import anorm._
import anorm.SqlParser._
import helpers.DbSetup._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.Play.{ start, stop }
import play.api.test.FakeApplication

class PersistentTagLoaderSpec extends Specification {

  val tagName = "taggy"

  trait TagSetup extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("TagLoaderSpec")
    lazy val tagLoader = new PersistentTagLoader()
  }

  step(start(FakeApplication()))

  "PersistentTagLoader" should {

    "get tag id by name if it exists" in new TagSetup {
      val tagId = insertTag(documentSetId, tagName)

      val foundTag = tagLoader.loadByName(documentSetId, tagName)

      foundTag must be equalTo (Some(tagId))
    }

    "get tag id by name from the correct document set" in new TagSetup {
      val tagId1 = insertTag(documentSetId, tagName)
      val documentSetId2 = insertDocumentSet("OtherDocumentSet")
      val tagId2 = insertTag(documentSetId2, tagName)
      
      val foundTag1 = tagLoader.loadByName(documentSetId, tagName)
      val foundTag2 = tagLoader.loadByName(documentSetId2, tagName)
      
      foundTag1 must beSome.like{ case t => t must be equalTo(tagId1) }
      foundTag2 must beSome.like{ case t => t must be equalTo(tagId2) }
    }

    "get None if tag does not exist" in new TagSetup {
      val missingTag = tagLoader.loadByName(documentSetId, tagName)

      missingTag must beNone
    }

    "count total number of documents tagged" in new TagSetup {
      val documentIds = for (i <- 1 to 5) yield insertDocument(documentSetId, "title", "documentCloudId")

      val tagId = insertTag(documentSetId, tagName)

      val initialCount = tagLoader.countDocuments(tagId)

      initialCount must be equalTo (0)

      tagDocuments(tagId, documentIds.take(3))

      val count = tagLoader.countDocuments(tagId)
      count must be equalTo (3)
    }

    "count tagged documents per node" in new TagSetup {
      val nodeIds = insertNodes(documentSetId, 4)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 4)

      val tagId = insertTag(documentSetId, tagName)

      tagDocuments(tagId, documentIds)
      val counts = tagLoader.countsPerNode(nodeIds, tagId)
      val expectedCounts = nodeIds.map((_, 4l))

      counts must haveTheSameElementsAs(expectedCounts)
    }

    "insert 0 values for nodes with no tagged documents" in new TagSetup {
      val nodeIds = insertNodes(documentSetId, 4)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 4)

      val tagId = insertTag(documentSetId, tagName)

      val initialCounts = tagLoader.countsPerNode(nodeIds, tagId)
      val expectedInitialCounts = nodeIds.map((_, 0l))

      initialCounts must haveTheSameElementsAs(expectedInitialCounts)

      tagDocuments(tagId, documentIds.take(8))

      val counts = tagLoader.countsPerNode(nodeIds, tagId)
      val expectedCounts = nodeIds.take(2).map((_, 4l)) ++ nodeIds.drop(2).map((_, 0l))

      counts must haveTheSameElementsAs(expectedCounts)
    }

    "empty list returns counts for all nodes" in new TagSetup {
      val nodeIds = insertNodes(documentSetId, 4)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 4)

      val tagId = insertTag(documentSetId, tagName)

      tagDocuments(tagId, documentIds)
      val counts = tagLoader.countsPerNode(Nil, tagId)
      val expectedCounts = nodeIds.map((_, 4l))

      counts must haveTheSameElementsAs(expectedCounts)
    }

    "return tag data for tag id" in new TagSetup {
      val nodeIds = insertNodes(documentSetId, 1)
      val documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, 4)
      val tagId = insertTag(documentSetId, tagName)
      tagDocuments(tagId, documentIds)

      val expectedTagData = documentIds.map(d => (tagId, tagName, 4, Some(d)))
      val tagData = tagLoader.loadTag(tagId)

      tagData must haveTheSameElementsAs(expectedTagData)
    }
  }

  step(stop)
}
