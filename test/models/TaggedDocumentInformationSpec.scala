package models

import helpers.DbSetup._
import helpers.DbTestContext
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

  class TaggedDocumentInformationSpec extends Specification with Mockito {
    step(start(FakeApplication()))

    "TaggedDocumentInformationwith" should {

      trait TaggedDocuments extends DbTestContext {
	lazy val documentSetId = insertDocumentSet("TaggedDocumentInformationSpec")
	val tagName = "a tag"
	val taggedDocuments = Seq(1l, 2l, 3l)
	val totalTaggedDocuments = 44l
	val documentList = models.core.DocumentIdList(taggedDocuments, totalTaggedDocuments)
	
	val loader = mock[PersistentTagLoader]
	val parser = mock[DocumentListParser]

	var tag: OverviewTag with TaggedDocumentInformation = _
	
	override def setupWithDb = {
	  val dummyDocumentListData = Seq((4l, Some(10l)))
	  val tagId = insertTag(documentSetId, tagName)

	  loader loadDocumentList(tagId) returns dummyDocumentListData
	  parser createDocumentIdList(dummyDocumentListData) returns documentList

	  val t = models.orm.Tag.findByName(documentSetId, tagName).getOrElse(throw new Exception("missing tag"))
	  
	  tag = new OverviewTag.TaggedDocumentInformationImpl(t, loader, parser)
	    
	}
      }
      
      "have documentIdList for tag" in new TaggedDocuments {

	tag.documentIds.firstIds must haveTheSameElementsAs(taggedDocuments)
	tag.documentIds.totalCount must beEqualTo(totalTaggedDocuments)
      }
    }

    step(stop)
  }
