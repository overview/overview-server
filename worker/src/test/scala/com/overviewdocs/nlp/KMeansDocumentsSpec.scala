/**
 * KMeansDocumentsSpec.scala
 * Tests both iterative (variable k) and non-iterative (fixed k) algorithms
 *
 * Overview, created January 2013
 * @author Jonathan Stray
 *
 */

package com.overviewdocs.nlp

import java.io.File
import org.specs2.mutable.Specification
import scala.Int.int2long
import DocumentVectorTypes._
import scala.util.Random

class KMeansDocumentsSpec extends Specification {
  sequential

  // load up some docs to play with
  def getSampleDocumentVectors: DocumentSetVectors = {
    // Super-wonky initialization logic ... oh well :)

    val stringTable = new StringTable()
    stringTable.stringToId("term0") // id 0
    stringTable.stringToId("term1") // id 1
    stringTable.stringToId("term2") // ...
    stringTable.stringToId("term3")
    stringTable.stringToId("term4")
    stringTable.stringToId("term5")

    val vectors = new DocumentSetVectors(stringTable)
    vectors.+=(0L -> new DocumentVector(Array(0, 1, 2, 4), Array(1f, 1f, 1f, 50f)))
    vectors.+=(1L -> new DocumentVector(Array(1, 2, 3, 4), Array(1f, 1f, 1f, 50f)))
    vectors.+=(2L -> new DocumentVector(Array(2, 3, 4, 5), Array(2f, 1f, 1f, 50f)))
    vectors.+=(3L -> new DocumentVector(Array(0, 1, 4, 5), Array(2f, 1f, 1f, 50f)))
    vectors.+=(4L -> new DocumentVector(Array(0, 2, 4, 5), Array(2f, 1f, 1f, 50f)))

    vectors
  }

  "kMeansDocuments" should {
    "find three clusters" in {
      val docVecs = getSampleDocumentVectors
      val km = new KMeansDocuments(docVecs)
      km.seedClusterSize = 1
      km.seedClusterSkip = 3

      val clusters = km(docVecs.keys.toArray.sorted, 3)
      clusters must beEqualTo(Array(0, 0, 1, 2, 2))
    }

    "find clusters using iterative algorithm" in {
      Random.setSeed(4) // always start form the same place

      val docVecs = getSampleDocumentVectors
      val km = new IterativeKMeansDocuments(docVecs)
      val clusters = km(docVecs.keys.toArray.sorted, 3)
      clusters must beEqualTo(Array(0, 0, 0, 0, 0)) // TODO make the algorithm produce good output here
    }
  }
}
