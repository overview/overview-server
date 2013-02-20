/**
 * KMeansDocumentsSpec.scala
 * 
 * Overview Project, created January 2013
 * @author Jonathan Stray
 * 
 */

import java.io.File
import org.overviewproject.clustering._
import org.overviewproject.clustering.ClusterTypes._
import org.overviewproject.util.CompactPairArray
import org.specs2.mutable.Specification
import scala.collection.mutable.Set

class KMeansDocumentsSpec extends Specification {

  // load up some docs to play with
  def getSampleDocumentVectors : DocumentSetVectors = {
    val vectorGen = new DocumentVectorGenerator()
    val filenames =  new File("worker/src/test/resources/docs").listFiles
    filenames foreach { filename =>
      vectorGen.addDocument(filename.hashCode, Lexer.makeTerms(io.Source.fromFile(filename).mkString))
    }
    vectorGen.documentVectors()
  } 
  
  "kMeansDocuments" should {
    "find three clusters" in {
      val docVecs = getSampleDocumentVectors
      val km = new KMeansDocuments(docVecs)
      km.seedClusterSize = 1
      km.seedClusterSkip = 3
      
      val clusters = km(docVecs.keys, 3)
      val clusterSizes = (0 until 3).map(i => clusters.count(_._2 == i))
      //println("cluster sizes: " + clusterSizes)
      //println("clusters: " + clusters)
      clusterSizes should haveTheSameElementsAs (Seq(4,2,3))
    }
  }
}
   