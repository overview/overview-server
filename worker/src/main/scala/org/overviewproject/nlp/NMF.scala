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
import org.overviewproject.nlp.DocumentVectorTypes._
import org.saddle._
import scala.reflect.ClassTag

class NMF(val docVecs:DocumentSetVectors) {

  type TopicID = Int
  
  // Matrix element type. Float would be better, but Saddle isn't specialized for Float yet
  type E = Double
  
  // Map column indices to document IDs in a consistent way
  val docColToId = docVecs.keys.toArray.sorted
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
    
    require(numCols == src.numCols)
    require(srcRow < src.numRows)
    //println(s"accumulateRow: src size (${src.numRows},${src.numCols}), numCols $numCols, dstRow $dstRow, srcRow $srcRow")
    
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
    val numTerms = docVecs.stringTable.size
    val numTopics = m.numCols
    require(m.numRows == numDocs)
    require(m.numCols == numTopics)

    //println(s"docsLeftMult: numDocs $numDocs, numTerms $numTerms, numTopics $numTopics")
    
    val outRaw = new Array[E](numTerms * numTopics) // NB: starts zeroed
    
    // loop over document vectors exactly once, equivalent to looping over rows of m
    // we add each row of a into the output rows corresponding to terms
    var inRow = 0
    while (inRow < numDocs) {
      val doc = docVecs(docColToId(inRow))
      val numTerms = doc.size
      
      // for each term in doc, add weight * row of m corresponding to term ID
      var t = 0
      while (t < numTerms) {
        val outRow = doc.terms(t)
        val weight = doc.weights(t)
        accumulateRow(outRaw, numTopics, outRow, m, inRow, weight)
        t += 1
      }
      inRow += 1
    }    
    
    Mat(numTerms, numTopics, outRaw)
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

/*
  def mapMatrix[E:ClassTag](m: Mat[E], f: (Int,Int,E) => E) : Mat[E] = {
    val rows = m.numRows
    val cols =  m.numCols
    val sz = rows*cols
    val out = new Array[E](sz)
    var idx = 0
    while (idx < sz) {
      val row = idx / cols
      val col = idx % cols
      out.update(idx, f(row, col, m.raw(row,col)))
      idx += 1
    }
    Mat(rows, cols, out)
  }
*/

  // computes m*num/denom, except where num==denom==0, leaves m unchanged
  def carefulMultDiv(m:Mat[E], num:Mat[E], denom:Mat[E]) : Mat[E] = {
    val rows = m.numRows
    val cols =  m.numCols
    val sz = rows * cols
    val out = new Array[E](sz)
    var idx = 0
    while (idx < sz) {
      val row = idx / cols
      val col = idx % cols
      val v = m.raw(row, col)
      val n = num.raw(row, col)
      val d = denom.raw(row, col)
      if (n==0 && d==0)
        out.update(idx, v)
      else
        out.update(idx, (v * n) / d)
      idx += 1
    }
    Mat(rows, cols, out)    
  }
  
  // In the methods below:
  //   V is the array of (sparse) document vectors, one document per column. We never build it explicitly, but reference docVecs
  //   W is the array of "topics", mapping each of r topics to weights on n terms
  //   H is the array of "coordinates", mapping each of m docs to weights on r topics
  
  // H <- H * (W'V) / (W'WH)
  // where ' is transpose and * / are element-wise
  def IterateH(W:Mat[E], H:Mat[E]) : Mat[E] = {

    val r = W.numCols   // number of topics
    val n = W.numRows   // vocabulary size
    val m = H.numCols   // number of documents
    require(W.numCols == H.numRows)

    val Wt = W.T
    val num = makeMatrix(r, m, (row,col) => docRowDotProduct(col, Wt, row))
    val denom = (Wt mult W) mult H

    // H * num / denom
    carefulMultDiv(H, num, denom)
  }

  // W <- W * (VH') / (WHH')
  // where ' is transpose and * / are element-wise
  def IterateW(W:Mat[E], H:Mat[E]) : Mat[E] = {
    val Ht = H.T
    
    val num = docsLeftMult(Ht)
    val denom = W mult (H mult Ht)
    
    //println(s"W num\n$num\nW denom\n$denom")
    
    // W * num / denom
    carefulMultDiv(W, num, denom)
  }

  def apply(numTopics:Int) : Pair[Mat[E], Mat[E]] = {
    val numDocs = docVecs.size
    val numTerms = docVecs.stringTable.size  // dimension of each docvec (though it's sparse)
    
    //println(s"NMF: $numDocs documents, $numTerms vocabulary size, $numTopics topics")
    
    // Initialize topic and coordinate arrays to random strictly positive values
    var W = mat.randp(numTerms,numTopics)
    var H = mat.randp(numTopics,numDocs)
    
    val maxIter = 20
    var iter = 0
    while (iter < maxIter) {
      //println(s"Starting iteration $iter")
      //println(s"W matrix, terms x topics\n$W")
      //println(s"H matrix, topics x docs\n$H")
      
      H = IterateH(W,H)
      W = IterateW(W,H)
      iter += 1
      
    }
    
    (W,H)
  }
}
