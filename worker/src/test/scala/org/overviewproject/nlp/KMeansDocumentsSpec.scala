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
  
    // Unfortunately iterative k-means is sensitive to document ordering, so changes in DocumentVectorGenerator can
    // change the output. On current test docs we seem to get either (4,3,2) or (5,4,0)
    "find clusters using iterative algorithm" in {
      val docVecs = getSampleDocumentVectors
      val km = new IterativeKMeansDocuments(docVecs)
      
      val clusters = km(docVecs.keys.toArray, 3)
      val clusterSizes = (0 until 3).map(i => clusters.count(_ == i))
      clusterSizes should haveTheSameElementsAs (Seq(4,5,0))
    }
  }
}
   