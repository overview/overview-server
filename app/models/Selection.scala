package models

import scala.collection.JavaConversions._

class Selection (tree: Tree) {
  def slice(start: Long, end: Long) : Seq[Document] = {
    val allSortedDocuments = tree.root.getDocuments.toSeq.sortBy(_.getTitle)
    
    allSortedDocuments.slice(start.toInt, end.toInt + 1)
  }
  
  def count = 0l
}