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

import org.overviewproject.nlp.DocumentVectorTypes._
import scala.collection.mutable.{Map, HashMap}

abstract class TreeLabelerBase {
  def apply(node:DocTreeNode) : Unit
}

class SimpleTreeLabeler(docVecs:DocumentSetVectors) extends TreeLabelerBase {

  // Turn a set of document vectors into a descriptive string. Takes top weighted terms, separates by commas
  private def makeDescription(vec: DocumentVectorBuilder): String = {
    val maxTerms = 15
    vec.toList.sortWith(_._2 > _._2).take(maxTerms).map(_._1).map(docVecs.stringTable.idToString(_)).mkString(", ")
  }

  // Create a descriptive string for each node, by taking the sum of all document vectors in that node.
  // Building all descriptions at once allows re-use of sub-sums -- quite important for running time.
  def labelNode(node: DocTreeNode): DocumentVectorBuilder = {

    if (node.docs.size == 1) {
      require(node.children.isEmpty)
      val vec = DocumentVectorBuilder(docVecs(node.docs.head)) // get document vector corresponding to our single document ID
      node.description += makeDescription(vec)
      vec
    } else {
      var vec = DocumentVectorBuilder()
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

    def isUnigramOfBigram(uni:String, bi:String) : Boolean = {
      val s = bi.indexOf("_")
      if (s == -1)
        return false      // not a bigram
        
      val u = bi.indexOf(uni)
      if (u == -1)
        return false      // does not contain unigram
        
      (u==0 && s == uni.length) ||                // first word
      (u==s+1 && bi.length == s+1 + uni.length)   // second word  
    }
    
    def removeThisUnigram(str:String, strs:List[String]) : Boolean = {
      strs.exists(isUnigramOfBigram(str, _))
    } 
    
    // Take top count terms and turn them into a list of strings, removing unigrams that are part of bigrams also in the liat
    def termsToString(vec:List[(TermID,TermWeightAndCount)], count:Int) = {
     val termIds = vec.take(count).map(_._1)
     val termStrings = termIds.map(docVecs.stringTable.idToString(_))
     val stripped = termStrings.filter(!removeThisUnigram(_, termStrings))
     stripped.mkString(", ") 
    }

    val numTopTerms = 30   // only consider terms with weight down to this rank    
    val topTerms = vec.toList.sortWith(_._2.weight > _._2.weight).take(numTopTerms)
    
    if (node.docs.size == 1) {
      val maxSingleDoc = 7
      termsToString(topTerms, maxSingleDoc)
      
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
