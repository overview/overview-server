/**
 * SuggestedTags.scala
 * Generates suggested tags list for documents and folders
 * 
 * Overview Project, created May 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import org.overviewproject.nlp.DocumentVectorTypes._
import scala.collection.mutable.{Map, HashMap}

object SuggestedTags {
 
  // Unigram trimming: don't include unigrams that appear as part of bigrams
  private def isUnigramOfBigram(uni:String, bi:String) : Boolean = {
    val s = bi.indexOf("_")
    if (s == -1)
      return false      // not a bigram
      
    val u = bi.indexOf(uni)
    if (u == -1)
      return false      // does not contain unigram
      
    (u==0 && s == uni.length) ||                // first word
    (u==s+1 && bi.length == s+1 + uni.length)   // second word  
  }
  
  private def removeThisUnigram(str:String, strs:List[String]) : Boolean = {
    strs.exists(isUnigramOfBigram(str, _))
  }
  
  private def removeUnigramsThatAppearInBigrams(strs:List[String]) = 
    strs.filter(!removeThisUnigram(_, strs))
      
  
  // --- Suggested tags for single document ---
    
  // Return suggested tags for a single document
  // Just a fixed number of the top terms, by TF-IDF weights
  def suggestedTagsForDocument(vec:DocumentVector, docVecs:DocumentSetVectors) : String = {
    val numSuggestedTagsForDoc = 7
    val terms = vec.toList.sortWith(_._2 > _._2).take(numSuggestedTagsForDoc).map(_._1).map(docVecs.stringTable.idToString(_))
    val filtered = removeUnigramsThatAppearInBigrams(terms)
    filtered.mkString(", ")
  }
    
  
  // --- Suggested tags for entire tree ---

  // Generate suggested tags for every node in a tree. Stores them in DocTreeNode.description
  def makeSuggestedTagsForTree (docVecs:DocumentSetVectors, root: DocTreeNode) : Unit = { 

    case class TermWeightAndCount(val weight:Float, val docCount:Int)
    type TermMap = HashMap[TermID, TermWeightAndCount]
    
    // Turn a set of document vectors into a descriptive string. Takes top weighted terms, separates by commas
    def makeDescription(node:DocTreeNode, vec: TermMap): String = {
      
      // Take top count terms and turn them into a list of strings, removing unigrams that are part of bigrams also in the list
      def termsToString(vec:List[(TermID,TermWeightAndCount)], count:Int) = {
       val termIds = vec.take(count).map(_._1)
       val termStrings = termIds.map(docVecs.stringTable.idToString(_))
       val stripped = removeUnigramsThatAppearInBigrams(termStrings)
       stripped.mkString(", ") 
      }
  
      val numTopTerms = 10   // only consider terms with weight down to this rank    
      val topTerms = vec.toList.sortWith(_._2.weight > _._2.weight).take(numTopTerms)
      
      if (node.docs.size == 1) {
        suggestedTagsForDocument(docVecs(node.docs.head), docVecs)
        
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

    // generates a TermMap for a single document
    def documentTermMap(vec:DocumentVector) : TermMap = {
      val tm = new TermMap()
      vec foreach { case (term,weight) => tm += (term -> TermWeightAndCount(weight, 1)) }           
      tm
    }
    
    // computes a += b, summing counts and weights
    def accumulateTermMap(a:TermMap, b:TermMap) : Unit = {
      b foreach { case (term, bwc) =>
        var awc = a.getOrElse(term, TermWeightAndCount(0,0))
        a += (term -> TermWeightAndCount(awc.weight + bwc.weight, awc.docCount + bwc.docCount))
      }
    }
  
    // Create the description string for each node, by taking the sum of all document vectors in that node.
    // Building all descriptions at once allows re-use of sub-sums -- hence linear (in nodes) running time.
    def labelNode(node: DocTreeNode): TermMap  = {
        
      val tm = new TermMap()
      
      if (node.children.isEmpty) {
        // Base case, this is a leaf node. Accumulate all vectors of all docs in this node
        for (doc <- node.docs) {
          accumulateTermMap(tm, documentTermMap(docVecs(doc))) 
        }
      } else {
        // Recursive case, accumulate term maps of children
        for (child <- node.children) {
          accumulateTermMap(tm, labelNode(child)) // sum the document vectors of all child nodes
        }
      }
      node.description += makeDescription(node, tm)
      tm
    }  
  
    // Main: start labeling at the root
    labelNode(root) 
  }
  
}