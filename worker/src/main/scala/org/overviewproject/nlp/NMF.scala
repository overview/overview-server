/**
 * nmf.scala
 * Non-negative matrix factorization - used for topic modeling on document vectors
 * 
 * Overview Project, created April 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.nlp
import org.overviewproject.clustering.ClusterTypes._
import org.saddle._
import scala.reflect.ClassTag

class NMF(val docVecs:DocumentSetVectors) {

  type TopicID = Int
  
  // Matrix element type. Float would be better, but Saddle isn't specialized for Float yet
  type E = Double
  
  // Map column indices to document IDs in a consistent way
  val docColToId = docVecs.keys.toArray
  def docIdToCol(id:DocumentID) = docColToId.indexOf(id)
  
  // This is a sparse dot product; also handles conversion between TermWeight and E
  def docRowDotProduct(docIdx:Int, m:Mat[E], row:Int) : E =  {
    val doc = docVecs(docColToId(docIdx))
    val n = doc.size    // number of non-zero terms 
    var res = 0.0
    var i = 0
    while (i < n) {
      res += doc.weights(i) * m.raw(row, doc.terms(i))
      i += 1
    }
    res
  }
  
  // computes dst[dstRow, _] += weight * src[srcRow, _] 
  // Sadly, because saddle.Mat is immutable, dst has to be a raw array
  def accumulateRow(dst:Array[E], numCols:Int, dstRow:Int, src:Mat[E], srcRow:Int, weight:E) : Unit = {
    
    var col = 0
    var dstIdx = dstRow*numCols
    while (col < numCols) {
      dst.update(dstIdx, dst(dstIdx) + weight * src.raw(srcRow, col))
      col += 1
      dstIdx += 1
    }
  }

  // Left multiply sparse document vectors v by matrix: out = vm
  // This is trickier than right multiplication, because the natural iteration order is across documents, not terms
  // We need to go across terms to exploit sparseness, so we do this by accumulating weights*rows of m into out
  def docsLeftMult(m:Mat[E]) : Mat[E] = {
    val numDocs = docVecs.size
    require(m.numRows == numDocs)

    val outRaw = new Array[E](m.numRows * m.numCols) // NB: starts zeroed
    
    // loop over document vectors exactly once, equivalent to looping over rows of m
    // we add each row of m into the output rows corresponding to terms
    var inRow = 0
    while (inRow < numDocs) {
      val doc = docVecs(docColToId(inRow))
      val numTerms = doc.size
      
      // for each term in doc, add weight * row of m corresponding to term ID
      var t = 0
      while (t < numTerms) {
        val outRow = doc.terms(t)
        val weight = doc.weights(t)
        accumulateRow(outRaw, m.numCols, outRow, m, inRow, weight)
        t += 1
      }
      inRow += 1
    }    
    
    Mat(m.numRows, m.numCols, outRaw)
  }
  
  // Takes a function from (row,column) to element, and constructs a new matrix
  def makeMatrix[E:ClassTag](rows:Int, cols:Int, f : (Int,Int) => E) : Mat[E] = {
    val sz = rows * cols
    val m = new Array[E](sz)
    var idx = 0
    while (idx < sz) {
      m(idx) = f(idx / cols, idx % cols)
      idx += 1
    }
    Mat(rows, cols, m)
  }

  
  // In the methods below:
  //   V is the array of (sparse) document vectors, one document per column. We never build it explicitly, but reference docVecs
  //   W is the array of "topics", mapping each of r topics to weights on n terms
  //   H is the array of "coordinates", mapping each of m docs to weights on r topics
  
  // H <- H * (W'V) / (W'WH)
  // where ' is transpose and * / are element-wise
  def IterateH(W:Mat[E], H:Mat[E]) : Mat[E] = {

    val r = W.numCols
    val n = W.numRows
    require(W.numCols == H.numRows)

    val Wt = W.T
    val num = makeMatrix(r, n, (row,col) => docRowDotProduct(col, Wt, row))
    val denom = (Wt mult W) mult H
          
    H * num / denom
  }

  // W <- W * (VH') / (WHH')
  // where ' is transpose and * / are element-wise
  def IterateW(W:Mat[E], H:Mat[E]) : Mat[E] = {
    val Ht = H.T
    
    val num = docsLeftMult(Ht)
    val denom = W mult (H mult Ht)
    
    W * num / denom
  }

  def apply(r:Int) : Pair[Mat[E], Mat[E]] = {
    val m = docVecs.size              // number of document vectors
    val n = docVecs.stringTable.size  // dimension of each docvec (though it's sparse)
    
    // Initialize topic and coordinate arrays to random strictly positive values
    var W = mat.randp(n,r)
    var H = mat.randp(r,m)
    
    val maxIter = 10
    var iter = 0
    while (iter < maxIter) {
      H = IterateH(W,H)
      W = IterateW(W,H)
      iter += 1
    }
    
    (W,H)
  }
}
