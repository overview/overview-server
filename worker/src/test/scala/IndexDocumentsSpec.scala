/**
 * IndexDocumentsSpec.scala
 * 
 * Unit tests for document set indexing: lexing and TF-IDF vector generation
 * 
 * Overview Project, created August 2012
 * @author Jonathan Stray
 * 
 */


import java.io.File
import scala.Array.canBuildFrom
import org.overviewproject.clustering.ClusterTypes.DocumentVectorMap
import org.specs2.mutable.Specification
import org.overviewproject.clustering.DocumentVectorGenerator
import org.overviewproject.clustering.Lexer

class MakeDocumentTreeSpec extends Specification {
  
  "Lexer" should {

    "remove stop words" in {
      Lexer.makeTerms("no i haha you") must beEqualTo(Seq("haha"))
    }
    
    "tokenize a complex string" in {
      val sentence = "the quick\t  brown.. Fox jump{s over\nyour 500 lAzy"
      val terms = Seq("quick", "brown", "fox", "jumps", "lazy")
      Lexer.makeTerms(sentence) must beEqualTo(terms)
    }
    
    "truncate long words" in {
      val longword = "thequickbrownfoxjumpsoverthelazydogthequickbrownfoxjumpsoverthelazydogthequickbrownfoxjumpsoverthelazydogthequickbrownfoxjumpsoverthelazydog"
      val sentence = "now is the time for all good " + longword + " to come to the aid of their module."
      Lexer.makeTerms(sentence).map(_.length).max must beEqualTo(Lexer.maxTokenLength)      
    }
  }    

  // must match computation in DocumentVector generator exactly, including rounding issues
  def computeIDF(numDocs:Int, occurences:Float) : Float = {
    math.log10(numDocs/occurences).toFloat
  }
  
  "DocumentVectorGenerator" should {
    
    "index a trivial document set" in {
      // we need at least 4 docs to get non-empty result, 
      // because DocumentVectorGenerator throws out any word that does not appear in at least 3 docs, or appears in all docs
      val doc1 = "the cat sat sat on the mat".split(" ")
      val doc2 = "the cat ate the rat".split(" ")
      val doc3 = "the rat sat on the mat mat mat".split(" ")
      val doc4 = "the rat doesn't really care about the cat cat".split(" ")
      	
      val vectorGen = new DocumentVectorGenerator()
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
 

    "index a complex document set" in {
      // This does not check actual IDF values but tests certain constraints on the output
      val vectorGen = new DocumentVectorGenerator()
      
      // load every doc in the test directory, generate terms and load into vector generator
      val filenames =  new File("worker/src/test/resources/docs").listFiles
      val docterms = filenames.map(filename => (filename, Lexer.makeTerms(io.Source.fromFile(filename).mkString)))
      for ((filename,terms) <- docterms) {
        vectorGen.addDocument(filename.hashCode, terms)
      }
      
      // check IDF
      val idf = vectorGen.Idf()
 
      // idf words must be drawn from total document vocabulary         
      val vocab = docterms.map(_._2).reduceLeft(_ ++ _).map(vectorGen.stringToId(_)).toSet
      val idfVocab = idf.map(_._1).toSet
      idfVocab.subsetOf(vocab) must beTrue       

      // all IDF terms must be in vocab
      // all IDF weights must be > 0, but <= than min number of docs to keep term (3) out of doc set size
      val maxIdf = math.log10(docterms.length / 3.0).toFloat
      for ((term,weight) <- idf) {
             vocab must contain(term)
             weight must beGreaterThan(0f)      
             weight must beLessThanOrEqualTo(maxIdf)
      }
      
           // check document vectors
      val vecs = vectorGen.documentVectors()
      vecs.size must beEqualTo(docterms.size) // ?? can we drop documents if no terms? probably not
      
      // each document vector must contain only terms in IDF and the source document, and be normalized
      for ((filename,terms) <- docterms) {
        val packedDocVec = vecs.get(filename.hashCode)
        packedDocVec must not beNull
        val docvec = DocumentVectorMap(packedDocVec.get)
        
        val docTerms = docvec.map(_._1).toSet
        docTerms.subsetOf(terms.map( vectorGen.stringToId(_) ).toSet) must beTrue    // all terms in vector must be in doc
        docTerms.subsetOf(idfVocab) must beTrue       // all terms in vector must be in IDF vocabulary
        
        val docWeights = docvec.map(_._2)
        val length = docWeights.foldLeft(0f)((sum,weight) => sum + weight*weight)
        length must beCloseTo(1f, 0.00001f)   // allowable numerical error in normalization
        for (weight <- docWeights) {
          weight must beGreaterThanOrEqualTo(0f)
          weight must beLessThanOrEqualTo(1f)       
        }
      }      
      
      success
    }
  }  
}
