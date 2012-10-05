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
  val noColor = None
  val noDocumentId = None

  trait TagSetup extends DbTestContext {
    lazy val documentSetId = insertDocumentSet("TagLoaderSpec")
    lazy val tagLoader = new PersistentTagLoader()
    var tagId: Long = _
    var tagData: Seq[(Long, String, Option[String], Long, Option[Long])] = _

    override def setupWithDb = {
      tagId = insertTag(documentSetId, tagName)
      tagData = Seq((tagId, tagName, noColor, 0, noDocumentId))
    }
  }

  trait NodesWithDocuments extends TagSetup {
    var nodeIds: Seq[Long] = _
    var documentIds: Seq[Long] = _

    val numberOfNodes = 4
    val documentsPerNode = 4

    override def setupWithDb = {
      super.setupWithDb

      nodeIds = insertNodes(documentSetId, numberOfNodes)
      documentIds = insertDocumentsForeachNode(documentSetId, nodeIds, documentsPerNode)
    }
  }

  trait TaggedDocuments extends NodesWithDocuments {
    override def setupWithDb = {
      super.setupWithDb

      tagDocuments(tagId, documentIds)
      val documentCount: Long = documentIds.size
      tagData = documentIds.take(10).map(d => (tagId, tagName, noColor, documentCount, Some(d)))
    }
  }

  step(start(FakeApplication()))

  "PersistentTagLoader" should {

    "get tag by name if it exists" in new TagSetup {
      val foundTagData = tagLoader.loadTagByName(documentSetId, tagName)

      foundTagData must haveTheSameElementsAs(tagData)
    }

    "get no data if tag does not exist" in new TagSetup {
      val notTagName = "not the name"
      val foundTagData = tagLoader.loadTagByName(documentSetId, notTagName)

      foundTagData must be empty
    }

    "get tag by name from the correct document set" in new TagSetup {
      val documentSetId2 = insertDocumentSet("OtherDocumentSet")
      val tagId2 = insertTag(documentSetId2, tagName)
      val otherTagData = Seq((tagId2, tagName, noColor, 0, noDocumentId))

      val foundTagData = tagLoader.loadTagByName(documentSetId, tagName)
      val otherFoundTagData = tagLoader.loadTagByName(documentSetId2, tagName)

      foundTagData must haveTheSameElementsAs(tagData)
      otherFoundTagData must haveTheSameElementsAs(otherTagData)
    }

    "get tagged document data" in new TaggedDocuments {
      val foundTagData = tagLoader.loadTagByName(documentSetId, tagName)

      foundTagData must haveTheSameElementsAs(tagData)
    }

    "get tag id by name if it exists" in new TagSetup {
      val foundTag = tagLoader.loadByName(documentSetId, tagName)

      foundTag must be equalTo (Some(tagId))
    }

    "get tag id by name from the correct document set" in new TagSetup {
      val documentSetId2 = insertDocumentSet("OtherDocumentSet")
      val tagId2 = insertTag(documentSetId2, tagName)

      val foundTag1 = tagLoader.loadByName(documentSetId, tagName)
      val foundTag2 = tagLoader.loadByName(documentSetId2, tagName)

      foundTag1 must beSome.like { case t => t must be equalTo (tagId) }
      foundTag2 must beSome.like { case t => t must be equalTo (tagId2) }
    }

    "get None if tag does not exist" in new TagSetup {
      val missingTag = tagLoader.loadByName(documentSetId, "no tag")

      missingTag must beNone
    }

    "count total number of documents tagged" in new NodesWithDocuments {
      val initialCount = tagLoader.countDocuments(tagId)
      initialCount must be equalTo (0)

      tagDocuments(tagId, documentIds.take(3))
      val countAfterTag = tagLoader.countDocuments(tagId)
      countAfterTag must be equalTo (3)
    }

    "count tagged documents per node" in new TaggedDocuments {
      val expectedCounts = nodeIds.map((_, documentsPerNode))
      val counts = tagLoader.countsPerNode(nodeIds, tagId)

      counts must haveTheSameElementsAs(expectedCounts)
    }

    "insert 0 values for nodes with no tagged documents" in new NodesWithDocuments {
      val expectedInitialCounts = nodeIds.map((_, 0l))
      val initialCounts = tagLoader.countsPerNode(nodeIds, tagId)

      initialCounts must haveTheSameElementsAs(expectedInitialCounts)

      tagDocuments(tagId, documentIds.take(2 * documentsPerNode))

      val taggedNodeCounts = nodeIds.take(2).map((_, documentsPerNode))
      val notTaggedNodeCounts = nodeIds.drop(2).map((_, 0l))
      val expectedCounts = taggedNodeCounts ++ notTaggedNodeCounts

      val counts = tagLoader.countsPerNode(nodeIds, tagId)
      counts must haveTheSameElementsAs(expectedCounts)
    }

    "empty list returns counts for all nodes" in new TaggedDocuments {
      val expectedCounts = nodeIds.map((_, documentsPerNode))
      val counts = tagLoader.countsPerNode(Nil, tagId)

      counts must haveTheSameElementsAs(expectedCounts)
    }

    "return tag data for tag id" in new TaggedDocuments {
      val totalTagged = numberOfNodes * documentsPerNode
      val defaultColor: Option[String] = None

      val expectedTagData = documentIds.take(10).map(d => (tagId, tagName, totalTagged, Some(d), defaultColor))
      val data = tagLoader.loadTag(tagId)

      data must haveTheSameElementsAs(expectedTagData)
    }

    "load document list data" in new TaggedDocuments {
      val totalTagged = numberOfNodes * documentsPerNode
      val expectedDocumentListData = documentIds.take(10).map(d => (totalTagged, Some(d)))

      val documentListData = tagLoader.loadDocumentList(tagId)

      documentListData must haveTheSameElementsAs(expectedDocumentListData)
    }
  }

  step(stop)
}
