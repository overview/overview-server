package models

import com.avaje.ebean.FetchConfig

import scala.collection.JavaConversions._ 

class PartiallyLoadedNode(nodeId: Long) {
	  val node = Node.find.byId(nodeId)

	def getDescription() : String = {
	  return node.getDescription()
	}
 
	def getDocuments(start: Int, end: Int) : Seq[Document] = {
	  //val n = Node.find.setId(nodeId).fetch("documents").orderBy("documents.title").findUnique()

	  val sortedDocuments = node.getDocuments.toSeq.sortBy(_.getTitle)
	  
	  return sortedDocuments.slice(start, end + 1)
	}
}