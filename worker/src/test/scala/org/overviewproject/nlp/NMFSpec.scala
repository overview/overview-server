/**
 * NMFSpec.scala
 * Tests both private-ish and public methods of NMF class
 * 
 * Overview Project, created April 2013
 * @author Jonathan Stray
 * 
 */

package org.overviewproject.nlp

import org.overviewproject.clustering.ClusterTypes._
import org.overviewproject.util.StringTable
import org.specs2.mutable.Specification
import org.saddle._

class NMFSpec extends Specification {
      
  // Convert between document vector and matrix representations.
  def docsToMatrix(nmf:NMF, vocabSize:Int) : Mat[Double] = {
    def numCols = nmf.docVecs.size
    def numRows = vocabSize
      
    def docsMatrixElement(row:Int, col:Int) = {
      val docId = nmf.docColToId(col)
      DocumentVectorMap(nmf.docVecs(docId)).getOrElse(row, 0.0f)
    }
    
    nmf.makeMatrix(numRows, numCols, (row, col) => docsMatrixElement(row,col))
  }
  
  // Generates document set vectors with documentID = matrix column
  def matrixToDocs(m:Mat[Double]) : DocumentSetVectors = {    
    val docVecs = new DocumentSetVectors(new StringTable)
    for (col <- 0 until m.numCols) {
      val doc = new DocumentVectorMap
      for (row <- 0 until m.numRows) {
        val weight = m.raw(row, col)
        if (weight != 0.0)
          doc += (row -> weight.asInstanceOf[TermWeight])
      }
      docVecs += (col.asInstanceOf[DocumentID] -> DocumentVector(doc))
    }
    docVecs
  } 
  
  // Create set with three sample documents, corresponding to the columns of the following matrix
  //       1  1  1
  //  v =  0  1  0
  //       0  1  2
  val numSampleDocs = 3
  val sampleVocabSize = 3  
  val sampleMat = Mat(sampleVocabSize, numSampleDocs, Array[Double]( 
                   1, 1, 1, 
                   0, 1, 0,
                   0, 1, 2))                   
  val firstTermDocId = 0
  val threeTermsDocId = 1
  val doubleLastTermId = 2 
  val sampleDocs = matrixToDocs(sampleMat)
  
  // maximum term ID in any of these documents
  
  "NMF" should {
    "docRowDotProduct" in {
      val nmf = new NMF(sampleDocs)
      val m = mat.rand(5, 3)  // as many columns as vocabulary size
      
      val testRow = 1
      
      nmf.docRowDotProduct(nmf.docIdToCol(firstTermDocId), m, testRow) should beCloseTo(m.raw(testRow,0), 1e-6) // document 1 should select only one val
      nmf.docRowDotProduct(nmf.docIdToCol(threeTermsDocId), m, testRow) should beCloseTo(m.takeRows(Array(testRow)).toVec.sum, 1e-6)
    }
    
    "accumulateRow" in { 
      val nmf = new NMF(sampleDocs)
      val dstRows = 5 
      val numCols = 4
      
      // Annoying: must use both Mat and raw arrays around here, as accumulateRow needs them, to write into 
      val dstRaw = new Array[Double](dstRows*numCols)
      val src = mat.rand(7,numCols) // same number of cols
      
      val dstRow = 1
      val srcRow = 2
      val weight = 0.5
      nmf.accumulateRow(dstRaw, numCols, dstRow, src, srcRow, weight)
      
      // back to Mat, for easy row sums for the test
      val dst = Mat(dstRows, numCols, dstRaw)
     
      // Check for correct row sum in m
      dst.takeRows(Array(dstRow)).toVec.sum should beCloseTo(weight * src.takeRows(Array(srcRow)).toVec.sum, 1e-6)
    }
    
    "docsLeftMult" in {
      val nmf = new NMF(sampleDocs)
      val numCols = sampleDocs.size
      val numRows = sampleVocabSize
      val m = mat.rand(numRows, numCols)

      val prod = nmf.docsLeftMult(m)

      // Generate reference correct result by turning document set into a dense matrix 
      val v = docsToMatrix(nmf, sampleVocabSize)
      
      val correct = v mult m
            
      prod must beEqualTo(correct)
    }
    
    "trival topics" in {
      success
    }
  }
}
   
