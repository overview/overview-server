package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SelectionSpec extends Specification {
  trait ParseDocumentIdsScope extends Scope {
    val documentSetId: Long = 1L
    val nodeIds: Seq[Long] = Seq(1L)
    val tagIds: Seq[Long] = Seq(1L)
    val documentIds: String
    val searchResultIds: Seq[Long] = Seq(1L)
    val untagged: Boolean = true
    
    lazy val selection = Selection(documentSetId, nodeIds, tagIds, documentIds, searchResultIds, untagged)
    def parsedDocumentIds = selection.documentIds
  }

  "Selection" should {
    "be constructed from Seq[Long]s" in new Scope {
      // Why test this? Because our constructor is a bit funky
      val selection = Selection(1L, Seq(2L), Seq(3L), Seq(4L), Seq(5L), true)
      selection.documentSetId must beEqualTo(1L)
      selection.nodeIds must beEqualTo(Seq(2L))
      selection.tagIds must beEqualTo(Seq(3L))
      selection.documentIds must beEqualTo(Seq(4L))
      selection.searchResultIds must beEqualTo(Seq(5L))
      selection.untagged must beTrue
      
    }

    "parse strings into Seq[Long]" in new ParseDocumentIdsScope {
      override val documentIds = "1,2,3"
      parsedDocumentIds must beEqualTo(Seq(1L, 2L, 3L))
    }
    
    "set untagged to true if magic tagId 0 when constructed from strings" in new Scope {
      import models.Selection._
      val selection = Selection(1l, "", "0", "", "", false)
      
      selection.tagIds must beEmpty
      selection.untagged must beTrue
    }
  }
}
