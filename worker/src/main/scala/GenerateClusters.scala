/*
 * GenerateClusters.scala
 * Given a set of document vectors, return a tree hierarchical tree of clusters
 * Based on an algorithm by Stephen Ingram
 * 
 * Overview Project
 * 
 * Created by Jonathan Stray, July 2012
 * 
 */

package clustering

import models._


case class SimpleNode(documentSet: DocumentSet, documents : Set[DocumentVectorGenerator#DocumentID], description: String)

class TreeBuilder(val docVecs : DocumentVectorGenerator#DocumentSetVectors) {
  
  // sparse dot product on two term->float maps
  def SparseDotCore(a: DocumentVectorGenerator#DocumentVector, b: DocumentVectorGenerator#DocumentVector) : Float = {
      a.foldLeft(0f) { case (sum, (term, weight)) => sum + b.getOrElse(term,0f) * weight }  
  }
  
  // fold by scanning the the shorter list, for efficiency
  def SparseDot(a: DocumentVectorGenerator#DocumentVector, b: DocumentVectorGenerator#DocumentVector) : Float = {
    if (a.size < b.size) 
      SparseDotCore(a,b) 
    else 
      SparseDotCore(b,a)  
  }
  
  // Document distance computation. Returns 1 - similarity, where similarity is cosine of normalized vectors
  def distance(a: DocumentVectorGenerator#DocumentVector, b: DocumentVectorGenerator#DocumentVector) = {
    1.0f - SparseDot(a,b)
  }
  
  // The sampling algorithm depends on a "transposed" index, mapping from terms->documents (instead of docs->terms)
  // Actually each entry is a priority queue
  case class TransposedTerm(term:String, weight:Float, nextIdx:Int)
  
  class TransposedIndex(val docVecs : DocumentVectorGenerator#DocumentSetVectors) {
    
  }
}
