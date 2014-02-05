/**
 * BuildDocTreeSpec.scala
 * 
 * Unit tests for tree generation: find connected components, build thresholded tree
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */

package org.overviewproject.clustering

import org.overviewproject.nlp.UnigramDocumentVectorGenerator
import org.specs2.mutable.Specification

class BuildDocTreeSpec extends Specification {

  "BuildDocTree" should {
    
    "separate empty and non-empty nodes" in {
      val vectorGen = new UnigramDocumentVectorGenerator
      vectorGen.addDocument(1, Seq("word1","word2"))
      vectorGen.addDocument(2, Seq("singular"))         // will be removed, N=1
      vectorGen.addDocument(3, Seq("word1","word2"))
      vectorGen.addDocument(4, Seq("word1","word2"))    // need at least three docs with same words as vector generator removes all words with N<3
      vectorGen.minDocsToKeepTerm = 3                   // this is the default, but changeable, so set here
      val docVecs = vectorGen.documentVectors
            
      val (nonEmpty, empty) = BuildDocTree.gatherEmptyDocs(docVecs)
            
      nonEmpty.docs should containTheSameElementsAs(Seq(1,3,4))
      empty.docs should containTheSameElementsAs(Seq(2))
    }
  }
  
}
