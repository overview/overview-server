/**
 * IndexDocumentsSpec.scala
 *
 * Unit tests for document set indexing: lexing and TF-IDF vector generation
 *
 * Overview, created August 2012
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.clustering

import java.io.File
import scala.Array.canBuildFrom
import com.overviewdocs.nlp.DocumentVectorTypes.DocumentVectorBuilder
import org.specs2.mutable.Specification
import com.overviewdocs.nlp.UnigramDocumentVectorGenerator
import com.overviewdocs.nlp.Lexer
import scala.Int.int2long
import com.overviewdocs.nlp.StopWordSet

class IndexDocumentsSpec extends Specification {
  val stopWords = StopWordSet("en", "")

  "DocumentVectorGenerator" should {

    "index a complex document set" in {
      // This does not check actual IDF values but tests certain constraints on the output
      val vectorGen = new UnigramDocumentVectorGenerator()

      // load every doc in the test directory, generate terms and load into vector generator
      val filenames = new File("src/test/resources/docs").listFiles
      val docterms = filenames.map(filename => (filename,
        Lexer.makeTerms(io.Source.fromFile(filename).mkString, stopWords)))
      for ((filename, terms) <- docterms) {
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
      for ((term, weight) <- idf) {
        vocab must contain(vectorGen.idfIdToString(term))
        weight must beGreaterThan(0f)
        weight must beLessThanOrEqualTo(maxIdf)
      }

      // check document vectors
      val vecs = vectorGen.documentVectors()
      vecs.size must beEqualTo(docterms.size) // must not have dropped any documents

      // each document vector must contain only terms in IDF and the source document, and be normalized
      for ((filename, terms) <- docterms) {
        val packedDocVec = vecs.get(filename.hashCode)
        packedDocVec must not beNull
        val docvec = DocumentVectorBuilder(packedDocVec.get)

        val docTerms = docvec.keys.map(vecs.stringTable.idToString(_)).toSet
        docTerms.subsetOf(terms.toSet) must beTrue // all terms in vector must be in doc
        docTerms.subsetOf(idfVocab) must beTrue // all terms in vector must be in IDF vocabulary

        val docWeights = docvec.map(_._2)
        val length = docWeights.foldLeft(0f)((sum, weight) => sum + weight * weight)
        length must beCloseTo(1f, 0.00001f) // allowable numerical error in normalization
        for (weight <- docWeights) {
          weight must beGreaterThanOrEqualTo(0f)
          weight must beLessThanOrEqualTo(1f)
        }
      }

      success
    }
  }
}
