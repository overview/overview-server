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
import scala.collection.mutable.{Map, HashMap}

abstract class TreeLabelerBase {
  def apply(node:DocTreeNode) : Unit
}

class SimpleTreeLabeler(docVecs:DocumentSetVectors) extends TreeLabelerBase {

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
      node.description += makeDescription(vec)
      vec
    } else {
      var vec = DocumentVectorMap()
      for (child <- node.children) {
        vec.accumulate(labelNode(child)) // sum the document vectors of all child nodes
      }
      node.description += makeDescription(vec)
      vec
    }
  }
  
  def apply(node:DocTreeNode) = labelNode(node) 
}

// Splits words into those that are in all docs in node, vs only some
class AllSomeTreeLabeler(docVecs:DocumentSetVectors) extends TreeLabelerBase {

  case class TermWeightAndCount(val weight:Float, val docCount:Int)
  type TermMap = HashMap[TermID, TermWeightAndCount]
  
  // Turn a set of document vectors into a descriptive string. Takes top weighted terms, separates by commas
  private def makeDescription(node:DocTreeNode, vec: TermMap): String = {

    def termsToString(vec:List[(TermID,TermWeightAndCount)], count:Int) = vec.take(count).map(_._1).map(docVecs.stringTable.idToString(_)).mkString(", ")

    val numTopTerms = 30   // only consider terms with weight down to this rank    
    val topTerms = vec.toList.sortWith(_._2.weight > _._2.weight).take(numTopTerms)
    
    if (node.docs.size == 1) {
      val maxSingleDoc = 5
      topTerms.take(maxSingleDoc).map(_._1).map(docVecs.stringTable.idToString(_)).mkString(", ")
      
    } else {
      // Take up to X docs in "all", Y docs in "most", Z docs in "some"
      val maxAll = 5
      val maxMost = 5
      val maxSome = 10
  
      val mostThresh = (Math.ceil(0.7 *  node.docs.size)).toInt
      
      val (inAllDocs, notInAllDocs) = topTerms.partition(_._2.docCount == node.docs.size)
      val (inMostDocs, inSomeDocs) = notInAllDocs.partition(_._2.docCount >= mostThresh)
      
      var out = ""
  
      if (inAllDocs.size>0)
        out += "ALL: " + termsToString(inAllDocs, maxAll)
        
      if (inMostDocs.size>0) {
        if (out.size > 0) out += "  "
        out += "MOST: " + termsToString(inMostDocs, maxMost)
      }
        
      if (inSomeDocs.size > 0) {
        if (out.size > 0) out += "  SOME: "
        out += termsToString(inSomeDocs, maxSome)
      }
      
      out
    }
  }

  // computes a += b, summing counts and weights
  private def accumulateTermMap(a:TermMap, b:TermMap) : Unit = {
    b foreach { case (term, bwc) =>
      var awc = a.getOrElse(term, TermWeightAndCount(0,0))
      a += (term -> TermWeightAndCount(awc.weight + bwc.weight, awc.docCount + bwc.docCount))
    }
  }
  
  // Create a descriptive string for each node, by taking the sum of all document vectors in that node.
  // Building all descriptions at once allows re-use of sub-sums -- quite important for running time.
  def labelNode(node: DocTreeNode): TermMap  = {

    if (node.docs.size == 1) {
      require(node.children.isEmpty)
      val vec = docVecs(node.docs.head) // get document vector corresponding to our single document ID
      val tm = new TermMap()
      vec foreach { case (term,weight) => tm += (term -> TermWeightAndCount(weight, 1)) } 
      node.description += makeDescription(node, tm)
      tm
    } else {
      val tm = new TermMap()
      for (child <- node.children) {
        accumulateTermMap(tm, labelNode(child)) // sum the document vectors of all child nodes
      }
      node.description += makeDescription(node, tm)
      tm
    }
  }  
  
  def apply(node:DocTreeNode) = labelNode(node) 
}
