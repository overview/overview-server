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

object ClusterTypes {
  type DocumentID = Long
  type TermWeight = Float
  
  type DocumentVector = mutable.Map[String, TermWeight ]                            // term -> tf_idf                                           
  def DocumentVector = mutable.Map[String, TermWeight ] _                           // so we can do var t = DocumentVector()
  
  type DocumentSetVectors = mutable.Map[DocumentID, DocumentVector]           // docid -> vector                  
  object DocumentSetVectors { def apply() = { mutable.Map[DocumentID, DocumentVector]() } }  
}