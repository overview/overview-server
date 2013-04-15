/**
 * DocumentVector.scala
 *  DocumentVectorTypes   - basic type defintions, plus classes below
 *  DocumentVectorBuilder - map from TermID to TermWeight, mutable
 *  DocumentVector        - immutable compact sparse vector format, containing an array of term IDs and an array of weights
 *  DocumentSetVectors    - mutable map from DocumentID to DocumentVector 
 *
 * Overview Project, created July 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.nlp

import scala.collection.mutable

object DocumentVectorTypes {

  type DocumentID = Long
  type TermWeight = Float
  type TermID = Int


  // --- DocumentVectorMap ----
  // This class represents documents as a straightforward mutable map from terms (encoded as IDs) to weights
  // Not very storage efficient, but good for lots of updates
  // Call .compact() to get final, memory-efficient DocumentVector
  
  // basically just map from term -> tf_idf
  // Supports += and all the normal insertion methods
  class DocumentVectorBuilder extends mutable.HashMap[TermID, TermWeight] { 
    
    def compact = DocumentVector(this)
    
    // this += v, implemented on sparse vectors
    def accumulate(v: DocumentVectorBuilder): Unit = {
      v foreach {
        case (id, weight) => update(id, getOrElse(id, 0f) + weight)
      }
    }
    
    // Also accumulate DocumentVector (array pair representation) directly
    def accumulate(v:DocumentVector) : Unit  = {
      for (i <- 0 until v.length) {
        update(v.terms(i), getOrElse(v.terms(i), 0f) + v.weights(i))
      }
    }
    
    // Multiply by scalar and accumulate. Good for stuff. Like weighted averages.  
    def multiplyAndAccumulate(factor:Float, v: DocumentVectorBuilder) : Unit = {
      v foreach {
        case (id, weight) => update(id, getOrElse(id, 0f) + factor*weight)
      }
    }

    def multiplyAndAccumulate(factor:Float, v: DocumentVector) : Unit = {
      for (i <- 0 until v.length) {
        update(v.terms(i), getOrElse(v.terms(i), 0f) + factor*v.weights(i))
      }
    }
    
  }

  object DocumentVectorBuilder {
    def apply() = new DocumentVectorBuilder()             // construct an empty object

    def apply(t:Pair[TermID,TermWeight]*) = {         // construct from pairs, like Map
      val d = new DocumentVectorBuilder
      t foreach { d+= _ }
      d
    }

    def apply(v:DocumentVector) = {                   // construct from DocumentVector's packed format
      val d = new DocumentVectorBuilder
      for (i <- 0 until v.length) 
        d += (v.terms(i) -> v.weights(i))
      d
    }
  }

  // --- DocumentVector ----
  // Document vector and vector set that has very efficient storage, but not mutable
  // - extracts terms and weights into separate arrays
  // - sorted by term ID for fast vector/vector products
  // But, also extends Traversable so appears as a sequence of Pair[TermID, TermWeight]
  
  class DocumentVector(val terms:Array[TermID], val weights:Array[TermWeight]) extends Traversable[Pair[TermID, TermWeight]] {
    
    require(terms.length == weights.length)
    
    def length = terms.length
    
    def foreach[U](f: Pair[TermID, TermWeight] => U): Unit = {
      for (i <- 0 until length)
        f((terms(i), weights(i)))
    }
  }
  
  // separate companion object because there is currently no way in scala to have temporary variables in the ctor that are not in the object
  // (in this case sortedTerms. see http://www.scala-lang.org/node/1198#comment-3430. js 10/9/2012)
  object DocumentVector {
    def apply(v:DocumentVectorBuilder) = {
      val sortedTerms = v.toList.sortWith(_._1 < _._1)
      val terms = Array[TermID](sortedTerms.map(_._1):_*)
      val weights = Array[TermWeight](sortedTerms.map(_._2):_*)
      
      new DocumentVector(terms, weights)
    }
  }
  
  // --- DocumentSetVectors ---
  // Set of vectors for all documents. Acts like a map from document ID -> vector
  // A set of document vectors is not interpretable without a StringTable, which must be provided to ctor
  class DocumentSetVectors(val stringTable: StringTable) extends mutable.HashMap[DocumentID, DocumentVector]  

  object DocumentSetVectors {
    def apply(stringTable: StringTable) = new DocumentSetVectors(stringTable)
  }  
}
