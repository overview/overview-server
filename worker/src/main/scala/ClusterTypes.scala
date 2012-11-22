/**
 * ClusterTypes.scala
 * Defines basic types for document vector handling and clustering
 *
 * Overview Project, created July 2012
 *
 * @author Jonathan Stray
 *
 */

package overview.clustering

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

object ClusterTypes {

  type DocumentID = Int
  type TermWeight = Float
  type TermID = Int

  // Simple little class that maintains a bidirectional map between term strings and term IDs
  class StringTable {

    private var _stringToId = mutable.Map[String, TermID]()
    private var _idToString = ArrayBuffer[String]()

    def stringToId(term: String): TermID = {
      _stringToId.getOrElseUpdate(term, { _idToString.append(term); _idToString.size - 1 })
    }

    def idToString(id: TermID): String = {
      _idToString(id)
    }
    
    def numTerms = _idToString.size
  }

  // --- DocumentVectorMap ----
  // This class represents documents as a straightforward mutable map from terms (encoded as IDs) to weights
  // Not very storage efficient, but good for lots of updates
  
  type DocumentVectorMap = mutable.Map[TermID, TermWeight] // term -> tf_idf

  object DocumentVectorMap {
    def apply() = mutable.Map[TermID, TermWeight]()     // construct an empty object

    def apply(t:Pair[TermID,TermWeight]*) = mutable.Map[TermID, TermWeight](t:_*)   // construct from pairs, like Map

    def apply(v:DocumentVector) = {                   // construct from DocumentVector's packed format
      val m = mutable.Map[TermID, TermWeight]()
      for (i <- 0 until v.length) 
        m += (v.terms(i) -> v.weights(i))
      m
    }
  }

  // --- DocumentVector, DocumentVectorSet ----
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
    def apply(v:DocumentVectorMap) = {
      val sortedTerms = v.toList.sortWith(_._1 < _._1)
      val terms = Array[TermID](sortedTerms.map(_._1):_*)
      val weights = Array[TermWeight](sortedTerms.map(_._2):_*)
      
      new DocumentVector(terms, weights)
    }
  }
  
  // Set of vectors for all documents. Acts like a map from document ID -> vector
  // A set of document vectors is not interpretable without a StringTable, which must be provided to ctor
  class DocumentSetVectors(val stringTable: StringTable) extends mutable.HashMap[DocumentID, DocumentVector]  

  object DocumentSetVectors {
    def apply(stringTable: StringTable) = new DocumentSetVectors(stringTable)
  }
  
  type DocumentDistanceFn = (DocumentVector, DocumentVector) => Double
  
  class SampledEdges extends mutable.HashMap[ DocumentID, mutable.Map[DocumentID, Double]] {
/*    type edgeMap = mutable.Map[DocumentID,Double] 
    def += (kv:(DocumentID,edgeMap)) = super.+=(kv)
    def -= (d:DocumentID) =  super.-=(d)
    def iterator = super.iterator
    def get(d:DocumentID) = super.get(d) */
  }

}
