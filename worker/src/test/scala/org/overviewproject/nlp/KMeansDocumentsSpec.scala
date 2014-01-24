/**
 * KMeansDocumentsSpec.scala
 * Tests both iterative (variable k) and non-iterative (fixed k) algorithms
 * 
 * Overview Project, created January 2013
 * @author Jonathan Stray
 * 
 */

package org.overviewproject.nlp

import java.io.File
import org.specs2.mutable.Specification
import scala.Int.int2long
import DocumentVectorTypes._
import scala.util.Random

class KMeansDocumentsSpec extends Specification {

  // load up some docs to play with
  def getSampleDocumentVectors : DocumentSetVectors = {
    val vectorGen = new UnigramDocumentVectorGenerator()
    val filenames =  new File("worker/src/test/resources/docs").listFiles.sorted
    filenames foreach { filename =>
      vectorGen.addDocument(filename.hashCode, Lexer.makeTerms(io.Source.fromFile(filename).mkString, StopWordSet("en", None)))
    }
    vectorGen.documentVectors()
  } 
  
  "kMeansDocuments" should {
    "find three clusters" in {
      val docVecs = getSampleDocumentVectors
      val km = new KMeansDocuments(docVecs)
      km.seedClusterSize = 1
      km.seedClusterSkip = 3
      
      val clusters = km(docVecs.keys.toArray, 3)
      val clusterSizes = (0 until 3).map(i => clusters.count(_ == i))
      //println("cluster sizes: " + clusterSizes)
      //println("clusters: " + clusters)
      clusterSizes should haveTheSameElementsAs (Seq(4,2,3))
    }
  
    // This is a randomized algorithm, and we don't do restarts so we often end local minima
    // In practice this is not too bad, but it makes testing hard, so we reset the random seed 
    // here to get consistent results. 
    "find clusters using iterative algorithm" in {
      Random.setSeed(1) // always start form the same place

      val docVecs = getSampleDocumentVectors
      val km = new IterativeKMeansDocuments(docVecs)
      
      val clusters = km(docVecs.keys.toArray, 3)
      val clusterSizes = (0 until 3).map(i => clusters.count(_ == i))
      clusterSizes should haveTheSameElementsAs (Seq(5,2,2))
    }
  }
}
   