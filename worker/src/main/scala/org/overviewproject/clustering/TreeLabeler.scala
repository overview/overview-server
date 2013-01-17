/**
 * TreeLabeler.scala
 * Sets DocTreeNode.description field by recursively computing top terms
 * 
 * Overview Project, created January 2013
 *
 * @author Jonathan Stray
 *
 */
package org.overviewproject.clustering

import org.overviewproject.clustering.ClusterTypes._

class TreeLabeler(docVecs:DocumentSetVectors) {

  // Turn a set of document vectors into a descriptive string. Takes top weighted terms, separates by commas
  private def makeDescription(vec: DocumentVectorMap): String = {
    val maxTerms = 15
    vec.toList.sortWith(_._2 > _._2).take(maxTerms).map(_._1).map(docVecs.stringTable.idToString(_)).mkString(", ")
  }

  // Create a descriptive string for each node, by taking the sum of all document vectors in that node.
  // Building all descriptions at once allows re-use of sub-sums -- quite important for running time.
  def labelNode(node: DocTreeNode): DocumentVectorMap = {

    if (node.docs.size == 1) {
      require(node.children.isEmpty)
      val vec = DocumentVectorMap(docVecs(node.docs.head)) // get document vector corresponding to our single document ID
      node.description = makeDescription(vec)
      vec
    } else {
      var vec = DocumentVectorMap()
      for (child <- node.children) {
        vec.accumulate(labelNode(child)) // sum the document vectors of all child nodes
      }
      node.description = makeDescription(vec)
      vec
    }
  }
}

