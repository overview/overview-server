/**
 * ClusterTypes.scala
 * Defines basic types for document vector handling and clustering
 * 
 * Overview Project, created July 2012
 * 
 * @author Jonathan Stray
 * 
 */

package clustering

object ClusterTypes {
  type DocumentID = Long
  type TermWeight = Float
  
  type DocumentVector = scala.collection.mutable.Map[String, TermWeight ]                            // term -> tf_idf                                           
  def DocumentVector = scala.collection.mutable.Map[String, TermWeight ] _                           // so we can do var t = DocumentVector()
  
  type DocumentSetVectors = scala.collection.mutable.Map[DocumentID, DocumentVector]           // docid -> vector                  
  object DocumentSetVectors { def apply() = { scala.collection.mutable.Map[DocumentID, DocumentVector]() } }  
}