/**
 * BigramDocumentVectorGeneratorSpec.scala
 * 
 * Overview Project, created March 2013
 * @author Jonathan Stray
 * 
 */

import org.overviewproject.clustering._
import org.overviewproject.clustering.ClusterTypes._
import org.specs2.mutable.Specification

class BigramDocumentVectorGeneratorSpec extends Specification {

  // must match computation in DocumentVector generator exactly, including rounding issues
  def computeIDF(numDocs:Int, occurences:Int) : Float = {
    math.log10(numDocs/occurences.toFloat).toFloat
  }
  
  "BigramDocumentVectorGenerator" should {
    
    "fail if zero documents" in {
      val vectorGen = new BigramDocumentVectorGenerator()
      vectorGen.documentVectors should throwA[NotEnoughDocumentsError]
    }
    
    "fail if one document" in {
      val vectorGen = new BigramDocumentVectorGenerator()
      vectorGen.addDocument(1, Seq("foo"))
      vectorGen.documentVectors should throwA[NotEnoughDocumentsError]
    }
    
    "count term frequency only with bigrams" in {
      val vectorGen = new BigramDocumentVectorGenerator
      vectorGen.minDocsToKeepTerm = 1     // keep all terms
      vectorGen.termFreqOnly = true       // don't apply IDF weighting (but doc vecs still normalized). Also keeps all bigrams.
                                          
      vectorGen.addDocument(1, Seq("word1","word2"))
      vectorGen.addDocument(2, Seq("word1","word2", "word2", "word3"))

      val docVecs = vectorGen.documentVectors
      val strs = docVecs.stringTable

      val id1 = strs.stringToId("word1")
      val id2 = strs.stringToId("word2")
      val id12 = strs.stringToId("word1_word2")
      val id22 = strs.stringToId("word2_word2")
      val id3 = strs.stringToId("word3")
      val id23 = strs.stringToId("word2_word3")
      
      val dv1 = DocumentVectorMap(docVecs(1))
      val rt3 = (1.0/Math.sqrt(3)).asInstanceOf[TermWeight] // 1*1 word1 + 1*1 word2 + 1*1 word1_word2
      dv1.size should beEqualTo(3)
      dv1(id1) should beCloseTo(rt3, 1e-6f)
      dv1(id2) should beCloseTo(rt3, 1e-6f)
      dv1(id12) should beCloseTo(rt3, 1e-6f)

      val dv2 = DocumentVectorMap(docVecs(2))
      val rt9 = (1.0/Math.sqrt((9))).asInstanceOf[TermWeight] // 1*1 word1  + 2*2 word2 + 1*1 word3 + 1*1 word1_word2 + 1*1 word2_word2 + 1*1 word2_word3 
      dv2.size should beEqualTo(6)
      dv2(id1) should beCloseTo(rt9, 1e-6f)
      dv2(id2) should beCloseTo(2*rt9, 1e-6f)
      dv2(id3) should beCloseTo(rt9, 1e-6f)
      dv2(id12) should beCloseTo(rt9, 1e-6f)
      dv2(id22) should beCloseTo(rt9, 1e-6f)
      dv2(id23) should beCloseTo(rt9, 1e-6f)
    }
  
    "compute TF-IDF if no bigrams" in {    
      val vectorGen = new BigramDocumentVectorGenerator()
      
      // Check defaults
      vectorGen.minDocsToKeepTerm should beEqualTo(3)
      vectorGen.keepTermsWhichAppearinAllDocs should beFalse // retain "the"
      vectorGen.termFreqOnly should beFalse
      vectorGen.minBigramOccurrences should beEqualTo(5)  // so we won't find any bigrams
      
      // we need at least 4 docs to get non-empty result, 
      // because DocumentVectorGenerator throws out any word that does not appear in at least 3 docs, or appears in all docs (like "the")
      val doc1 = "the cat sat sat on the mat".split(" ")
      val doc2 = "the cat ate the rat".split(" ")
      val doc3 = "the rat sat on the mat mat mat".split(" ")
      val doc4 = "the rat doesn't really care about the cat cat".split(" ")
      vectorGen.addDocument(1, doc1)
      vectorGen.addDocument(2, doc2)
      vectorGen.addDocument(3, doc3)
      vectorGen.addDocument(4, doc4)
      val vecs = vectorGen.documentVectors()
       
      // Lookup the ID's of the words we're going to check
      val strs = vecs.stringTable
      var catId = strs.stringToId("cat")
      var ratId = strs.stringToId("rat")
      
      // doc1: only cat remains
      vecs(1).terms(0) must beEqualTo(catId)
      DocumentVectorMap(vecs(1)) must beEqualTo(Map(catId->1.0)) 
      
      // doc2: cat and rat have same freq, vector normalized
      val sqrhalf = math.sqrt(0.5).toFloat
      DocumentVectorMap(vecs(2)) must_== Map(ratId->sqrhalf, catId->sqrhalf) 

      // doc3: only rat remains
      DocumentVectorMap(vecs(3)) must beEqualTo(Map(ratId->1.0))  // only rat appears in 3 docs
      
      // doc4: cat and rat remain, with different weights (which we recompute from scratch here)
      val ratTfIdf = doc4.count(_ == "rat").toFloat / doc4.length * computeIDF(4,3)
      val catTfIdf = doc4.count(_ == "cat").toFloat / doc4.length * computeIDF(4,3)
      val len = math.sqrt(ratTfIdf*ratTfIdf + catTfIdf*catTfIdf).toFloat
      DocumentVectorMap(vecs(4)) must beEqualTo(Map(ratId->ratTfIdf/len, catId->catTfIdf/len))
    }  
    
    "find trival bigrams" in {
      val vectorGen = new BigramDocumentVectorGenerator()
      
      // Set low thresholds so we keep all terms and find all bigrams that occur more than once
      vectorGen.minDocsToKeepTerm = 1
      vectorGen.keepTermsWhichAppearinAllDocs = true  // retain "the" and the many bigrams which contain it
      vectorGen.minBigramOccurrences = 2
      vectorGen.minBigramLikelihood = Double.NegativeInfinity                
      vectorGen.termFreqOnly should beFalse
      
      val doc1 = "the cat sat sat on the mat".split(" ")
      val doc2 = "the cat ate the rat".split(" ")
      val doc3 = "the rat sat on the mat mat mat".split(" ")
      val doc4 = "the rat doesn't really care about the cat cat".split(" ")
      vectorGen.addDocument(1, doc1)
      vectorGen.addDocument(2, doc2)
      vectorGen.addDocument(3, doc3)
      vectorGen.addDocument(4L<<40, doc4)  // test 64 bit doc ID
      
      val vecs = vectorGen.documentVectors()
      
      // We check that the appropriate bigrams were kept by looking up their names in the string table
      val strs = vecs.stringTable
      
      // these bigrams we must have ("mat_mat" because it appears twice in "mat mat mat")
      val bigrams = Seq("the_cat", "sat_on", "on_the", "the_mat", "the_rat", "mat_mat")
      bigrams should haveAllElementsLike { case b => strs.stringToIdFailIfMissing(b) should beGreaterThanOrEqualTo(0) }
      
      // these must not be detected as bigrams, because they occur only once
      val notBigrams = Seq("cat_sat", "sat_sat", "cat_ate", "ate_the", "rat_sat",  
                           "rat_doesn't", "doesn't_really", "really_care", "care_about", "about_the", "cat_cat")
      notBigrams should haveAllElementsLike { case b => strs.stringToIdFailIfMissing(b) should throwA[java.util.NoSuchElementException] } 
    }
  }

}
   