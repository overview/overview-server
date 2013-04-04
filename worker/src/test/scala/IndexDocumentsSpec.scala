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
import org.overviewproject.clustering.{UnigramDocumentVectorGenerator,NotEnoughDocumentsError}
import org.overviewproject.clustering.Lexer

class IndexDocumentsSpec extends Specification {
  
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

  
  "DocumentVectorGenerator" should {
    
    "index a complex document set" in {
      // This does not check actual IDF values but tests certain constraints on the output
      val vectorGen = new UnigramDocumentVectorGenerator()
      
      // load every doc in the test directory, generate terms and load into vector generator
      val filenames =  new File("worker/src/test/resources/docs").listFiles
      val docterms = filenames.map(filename => (filename, Lexer.makeTerms(io.Source.fromFile(filename).mkString)))
      for ((filename,terms) <- docterms) {
        vectorGen.addDocument(filename.hashCode, terms)
      }
      
      // check IDF
      val idf = vectorGen.Idf()

      // idf words must be drawn from total document vocabulary         
      val vocab = docterms.map(_._2).reduceLeft(_ ++ _).toSet
      val idfVocab = idf.keys.map(vectorGen.idfIdToString(_)).toSet
      idfVocab.subsetOf(vocab) must beTrue       

      // all IDF terms must be in vocab
      // all IDF weights must be > 0, but <= than min number of docs to keep term (3) out of doc set size
      val maxIdf = math.log10(docterms.length / 3.0).toFloat
      for ((term,weight) <- idf) {
             vocab must contain(vectorGen.idfIdToString(term))
             weight must beGreaterThan(0f)      
             weight must beLessThanOrEqualTo(maxIdf)
      }
      
      // check document vectors
      val vecs = vectorGen.documentVectors()
      vecs.size must beEqualTo(docterms.size) // must not have dropped any documents 
      
      // each document vector must contain only terms in IDF and the source document, and be normalized
      for ((filename,terms) <- docterms) {
        val packedDocVec = vecs.get(filename.hashCode)
        packedDocVec must not beNull
        val docvec = DocumentVectorMap(packedDocVec.get)
        
        val docTerms = docvec.keys.map(vecs.stringTable.idToString(_)).toSet
        docTerms.subsetOf(terms.toSet) must beTrue    // all terms in vector must be in doc
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
