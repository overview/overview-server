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

  type DocumentID = Long
  type TermWeight = Float
  type TermID = Int
  
  // Simple little class that maintains a bidirectional map between term strings and term IDs
  class StringTable {
    
    private var _stringToId = mutable.Map[String, TermID]()
    private var _idToString = ArrayBuffer[String]()
    
    def stringToId(term: String) : TermID = {
      _stringToId.getOrElseUpdate(term, { _idToString.append(term); _idToString.size-1 } )
    }
    
    def idToString(id : TermID) : String = {
      _idToString(id)
    }
  }
  
  // Vector for a single document is term ID -> weight
  type DocumentVector = mutable.Map[TermID, TermWeight ]                            // term -> tf_idf                                           
  def DocumentVector = mutable.Map[TermID, TermWeight ] _                           // so we can do var t = DocumentVector()
  
  // Vectors for an entire document set must reference a string table
  class DocumentSetVectors(val stringTable: StringTable) extends mutable.HashMap[DocumentID, DocumentVector] {        // docid -> vector                  
  }

  object DocumentSetVectors { 
    def apply(stringTable:StringTable) = new DocumentSetVectors(stringTable)
  }  
}
