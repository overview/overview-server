package models

import scala.collection.JavaConversions._

class Selection (tree: Tree) {
  def slice(start: Long, end: Long) : Seq[Document] = {
    tree.root.getDocuments.toSeq.sortBy(_.getTitle)
  }
  
  def count = 0l
}