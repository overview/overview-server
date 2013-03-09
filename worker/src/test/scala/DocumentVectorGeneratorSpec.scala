/**
 * DocumentVectorGeneratorSpec.scala
 * 
 * Overview Project, created March 2013
 * @author Jonathan Stray
 * 
 */

import org.overviewproject.clustering._
import org.overviewproject.clustering.ClusterTypes._
import org.specs2.mutable.Specification

class DocumentVectorGeneratorSpec extends Specification {

  // must match computation in DocumentVector generator exactly, including rounding issues
  def computeIDF(numDocs:Int, occurences:Float) : Float = {
    math.log10(numDocs/occurences).toFloat
  }
  
  "DocumentVectorGenerator" should {
    
    "fail if zero documents" in {
      val vectorGen = new DocumentVectorGenerator()
      vectorGen.documentVectors should throwA[NotEnoughDocumentsError]
    }
    
    "fail if one document" in {
      val vectorGen = new DocumentVectorGenerator()
      vectorGen.addDocument(1, Seq("foo"))
      vectorGen.documentVectors should throwA[NotEnoughDocumentsError]
    }
    
    "count term frequency only" in {
      val vectorGen = new DocumentVectorGenerator
      vectorGen.addDocument(1, Seq("word1","word2"))
      vectorGen.addDocument(2, Seq("word1","word2", "word2", "word3"))
      vectorGen.minDocsToKeepTerm = 1     // keep all terms
      vectorGen.termFreqOnly = true       // don't apply IDF weighting (but doc vecs still normalized)
      val docVecs = vectorGen.documentVectors

      val id1 = vectorGen.stringToId("word1")
      val id2 = vectorGen.stringToId("word2")
      val id3 = vectorGen.stringToId("word3")

      Seq(id1, id2, id3) should beSorted  // term ids should increase in order of first appearance 
      
      val dv1 = docVecs(1)
      val rt2 = (1.0/Math.sqrt(2)).asInstanceOf[TermWeight]
      dv1.length should beEqualTo(2)
      dv1.terms(0) should beEqualTo(id1)  // this also checks that terms are sorted in DocumentVector        
      dv1.weights(0) should beCloseTo(rt2, 1e-6f)
      dv1.terms(1) should beEqualTo(id2)          
      dv1.weights(1) should beCloseTo(rt2, 1e-6f)

      val dv2 = docVecs(2)
      val rt6 = (1.0/Math.sqrt(6)).asInstanceOf[TermWeight]
      dv2.length should beEqualTo(3)
      dv2.terms(0) should beEqualTo(id1)          
      dv2.weights(0) should beCloseTo(rt6, 1e-6f)
      dv2.terms(1) should beEqualTo(id2)
      dv2.weights(1) should beCloseTo(2*rt6, 1e-6f)
      dv2.terms(2) should beEqualTo(id3)
      dv2.weights(2) should beCloseTo(rt6, 1e-6f)
    }
  
  
    "compute TF-IDF" in {    
      val vectorGen = new DocumentVectorGenerator()
      
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
       
      // Lookup the ID's of the words we're going to check
      val catId = vectorGen.stringToId("cat")
      val ratId = vectorGen.stringToId("rat")
      
      // Check intermediate inverse document frequency (idf) vals. In this case only terms which appear in 3 docs are preserved
      val idf = vectorGen.Idf()
      idf(catId) must beEqualTo(computeIDF(4,3)) 
      idf(ratId) must beEqualTo(computeIDF(4,3))
      idf.size must beEqualTo(2)      
      
      // Finally, check actual vectors. 
      val vecs = vectorGen.documentVectors()
       
      // doc1: only cat remains
      DocumentVectorMap(vecs(1)) must beEqualTo(Map(catId->1.0)) 
      
      // doc2: cat and rat have same freq, vector normalized
      val sqrhalf = math.sqrt(0.5).toFloat
      DocumentVectorMap(vecs(2)) must_== Map(ratId->sqrhalf, catId->sqrhalf) 

      // doc3: only rat remains
      DocumentVectorMap(vecs(3)) must beEqualTo(Map(ratId->1.0))  // only rat appears in 3 docs
      
      // doc4: cat and rat remain, with different weights (which we recompute from scratch here)
      val ratTfIdf = doc4.count(_ == "rat").toFloat / doc4.length * idf(ratId)
      val catTfIdf = doc4.count(_ == "cat").toFloat / doc4.length * idf(catId)
      val len = math.sqrt(ratTfIdf*ratTfIdf + catTfIdf*catTfIdf).toFloat
      DocumentVectorMap(vecs(4)) must beEqualTo(Map(ratId->ratTfIdf/len, catId->catTfIdf/len))
    }
  }
}
   