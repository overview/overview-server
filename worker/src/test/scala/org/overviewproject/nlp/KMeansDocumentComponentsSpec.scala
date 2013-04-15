/**
 * KMeansDocumentComponentsSpec.scala
 * 
 * Overview Project, created March 2013
 * @author Jonathan Stray
 * 
 */

package org.overviewproject.nlp

import org.overviewproject.nlp.DocumentVectorTypes._
import org.specs2.mutable.Specification
import org.overviewproject.clustering.DocumentComponent

class KMeansDocumentComponentsSpec extends Specification {
  
  // Document set we use for all tests
  val vectorGen = new UnigramDocumentVectorGenerator
  vectorGen.addDocument(1, Seq("word1","word2"))
  vectorGen.addDocument(2, Seq("word2","word3"))
  vectorGen.addDocument(3, Seq("word3","word4"))
  vectorGen.termFreqOnly = true       // don't apply IDF weighting (make test values below much simpler)
  vectorGen.minDocsToKeepTerm = 1
  val docVecs = vectorGen.documentVectors
  val strs = docVecs.stringTable

  "DocumentComponent" should {
    
    "fail when given empty documents" in {
      new DocumentComponent(Set[DocumentID](), docVecs) should throwA[java.lang.IllegalArgumentException]
    }
    
    "construct from a set of documents" in {
      val docs = Set[DocumentID](1, 2, 3)
      val component = new DocumentComponent(docs, docVecs)
      
      component.nDocs should beEqualTo(3)
      docs should haveTheSameElementsAs(component.docs)
      
      // The centroid should be equal to (word1->1, word2->2, word3->2, word4->1) normalized, so divided by sqrt(10)
      val id1 = strs.stringToId("word1")
      val id2 = strs.stringToId("word2")
      val id3 = strs.stringToId("word3")
      val id4 = strs.stringToId("word4")
      val w1 = (1.0 / Math.sqrt(10)).asInstanceOf[TermWeight]
      val w2 = (2.0 / Math.sqrt(10)).asInstanceOf[TermWeight]
      val correctCentroid = DocumentVectorBuilder(id1->w1, id2->w2, id3->w2, id4->w1)
      DocumentVectorBuilder(component.centroid) should beEqualTo(correctCentroid)
    }
  }

  // TODO: add tests based on actual clustering... though the base IterativeKMeans is extensively tested already
}
   