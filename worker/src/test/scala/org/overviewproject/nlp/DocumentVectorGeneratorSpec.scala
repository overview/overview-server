/**
 * DocumentVectorGeneratorSpec.scala
 * 
 * Overview Project, created March 2013
 * @author Jonathan Stray
 * 
 */

package org.overviewproject.nlp

import org.overviewproject.nlp.DocumentVectorTypes._
import org.specs2.mutable.Specification

class DocumentVectorGeneratorSpec extends Specification {

  // must match computation in DocumentVector generator exactly, including rounding issues
  def computeIDF(numDocs:Int, occurences:Int) : Float = {
    math.log10(numDocs/occurences.toFloat).toFloat
  }
  
  "UnigramDocumentVectorGenerator" should {
    
    "fail if zero documents" in {
      val vectorGen = new UnigramDocumentVectorGenerator()
      vectorGen.documentVectors should throwA[NotEnoughDocumentsError]
    }
    
    "fail if one document" in {
      val vectorGen = new UnigramDocumentVectorGenerator()
      vectorGen.addDocument(1, Seq("foo"))
      vectorGen.documentVectors should throwA[NotEnoughDocumentsError]
    }
    
    "count term frequency only" in {
      val vectorGen = new UnigramDocumentVectorGenerator
      vectorGen.addDocument(1, Seq("word1","word2"))
      vectorGen.addDocument(2, Seq("word1","word2", "word2", "word3"))
      vectorGen.minDocsToKeepTerm = 1     // keep all terms
      vectorGen.termFreqOnly = true       // don't apply IDF weighting (but doc vecs still normalized)
      val docVecs = vectorGen.documentVectors

      val id1 = docVecs.stringTable.stringToId("word1")
      val id2 = docVecs.stringTable.stringToId("word2")
      val id3 = docVecs.stringTable.stringToId("word3")
      
      val dv1 = DocumentVectorBuilder(docVecs(1))
      val rt2 = (1.0/Math.sqrt(2)).asInstanceOf[TermWeight]
      dv1.size should beEqualTo(2)
      dv1(id1) should beCloseTo(rt2, 1e-6f)
      dv1(id2) should beCloseTo(rt2, 1e-6f)

      val dv2 = DocumentVectorBuilder(docVecs(2))
      val rt6 = (1.0/Math.sqrt(6)).asInstanceOf[TermWeight]
      dv2.size should beEqualTo(3)
      
      dv2(id1) should beCloseTo(rt6, 1e-6f)
      dv2(id2) should beCloseTo(2*rt6, 1e-6f)
      dv2(id3) should beCloseTo(rt6, 1e-6f)
    }
  
  
    "compute TF-IDF" in {    
      val vectorGen = new UnigramDocumentVectorGenerator()
      
      // Check defaults
      vectorGen.minDocsToKeepTerm should beEqualTo(3)
      vectorGen.termFreqOnly should beFalse
      
      // we need at least 4 docs to get non-empty result, 
      // because DocumentVectorGenerator throws out any word that does not appear in at least 3 docs, or appears in all docs
      val doc1 = "the cat sat sat on the mat".split(" ")
      val doc2 = "the cat ate the rat".split(" ")
      val doc3 = "the rat sat on the mat mat mat".split(" ")
      val doc4 = "the rat doesn't really care about the cat cat".split(" ")
      vectorGen.addDocument(1, doc1)
      vectorGen.addDocument(2, doc2)
      vectorGen.addDocument(3, doc3)
      vectorGen.addDocument(4, doc4)
       
      // Generate IDF table
      val idf = vectorGen.Idf()
      var catId = vectorGen.idfStringToId("cat")
      var ratId = vectorGen.idfStringToId("rat")

      // Check intermediate inverse document frequency (idf) vals. In this case only terms which appear in 3 docs are preserved
      idf(catId) must beEqualTo(computeIDF(4,3)) 
      idf(ratId) must beEqualTo(computeIDF(4,3))
      idf.size must beEqualTo(2)      

      // now produce final document vectors, and get new term ids (they'll change from the IDF table)
      val vecs = vectorGen.documentVectors()
      catId = vecs.stringTable.stringToId("cat")
      ratId = vecs.stringTable.stringToId("rat")

      // doc1: only cat remains
      vecs(1).terms(0) must beEqualTo(catId)
      DocumentVectorBuilder(vecs(1)) must beEqualTo(Map(catId->1.0)) 
      
      // doc2: cat and rat have same freq, vector normalized
      val sqrhalf = math.sqrt(0.5).toFloat
      DocumentVectorBuilder(vecs(2)) must_== Map(ratId->sqrhalf, catId->sqrhalf) 

      // doc3: only rat remains
      DocumentVectorBuilder(vecs(3)) must beEqualTo(Map(ratId->1.0))  // only rat appears in 3 docs
      
      // doc4: cat and rat remain, with different weights (which we recompute from scratch here)
      val ratTfIdf = doc4.count(_ == "rat").toFloat / doc4.length * computeIDF(4,3)
      val catTfIdf = doc4.count(_ == "cat").toFloat / doc4.length * computeIDF(4,3)
      val len = math.sqrt(ratTfIdf*ratTfIdf + catTfIdf*catTfIdf).toFloat
      DocumentVectorBuilder(vecs(4)) must beEqualTo(Map(ratId->ratTfIdf/len, catId->catTfIdf/len))
    }
  }
}
   