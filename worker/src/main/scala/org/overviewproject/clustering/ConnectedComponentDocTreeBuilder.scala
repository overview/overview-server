/**
 * ConnectedComponentDocTreeBuilder.scala
 * Document clustering algorithm based on connected components
 * Original algorithm by Stephen Ingram
 * Also, a Hybrid k-means/connected components algorithm
 * 
 * DEPRECATED - this algorithm is no longer used, as it produces too much "dust." Recursive k-means is superior. 
 *
 * Overview Project, created July 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import org.overviewproject.util.DocumentSetCreationJobStateDescription.ClusteringLevel
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress, NoProgressReporting }
import org.overviewproject.nlp.DocumentVectorTypes._
import scala.util.control.Breaks._

class ConnectedComponentDocTreeBuilder(protected val docVecs: DocumentSetVectors) {
  
  private val cc = new ConnectedComponentsDocuments(docVecs)

  // Expand out the nodes of the tree by thresholding the documents in each and seeing if they split into components
  // Returns new set of leaf nodes
  private def ExpandTree(currentLeaves: List[DocTreeNode], thresh: Double) = {
    var nextLeaves = List[DocTreeNode]()

    for (node <- currentLeaves) {

      val childComponents = cc.allComponents(node.docs, thresh)

      if (childComponents.size == 1) {
        // lower threshold did not split this component, pass unchanged to next level
        nextLeaves = node :: nextLeaves
      } else {
        // lower threshold did split this component, create a child TreeNode for each resulting component
        for (component <- childComponents) {
          val newLeaf = new DocTreeNode(component)
          node.children += newLeaf
          nextLeaves = newLeaf :: nextLeaves
        }
      }
    }

    nextLeaves
  }

  // --- public --- 
  
  def buildNodeSubtree(root:DocTreeNode, threshSteps: Seq[Double], progAbort: ProgressAbortFn) : Unit = {
    val numSteps: Double = threshSteps.size

    // intermediate levels created by successively thresholding all edges, (possibly) breaking each component apart
    var currentLeaves = List(root)

    breakable {
      for (curStep <- 1 to threshSteps.size - 2) {
        if (progAbort(Progress(curStep / numSteps, ClusteringLevel(curStep + 1)))) break
        currentLeaves = ExpandTree(currentLeaves, threshSteps(curStep))
      }
    }

    // bottom level thresh=0.0 is one leaf node for each document
    if (!progAbort(Progress((numSteps - 1) / numSteps, ClusteringLevel(numSteps.toInt)))) {
      for (node <- currentLeaves) {
        if (node.docs.size > 1) // don't expand if already one node
          node.children = node.docs.map(item => new DocTreeNode(Set(item)))
      }
    }   
  }
   
  // Steps distance thresh along given sequence. First step must always be 1 = full graph, 0 must always be last = leaves
  def BuildTree(root:DocTreeNode, threshSteps: Seq[Double], progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    require(threshSteps.head == 1.0)
    require(threshSteps.last == 0.0)
    require(threshSteps.forall(step => step >= 0 && step <= 1.0))

    // root thresh=1.0 is one node with all documents
    progAbort(Progress(0, ClusteringLevel(1)))

    buildNodeSubtree(root, threshSteps, progAbort)
    
    root
  }

  // version which sets root = all docs
  def BuildFullTree(threshSteps: Seq[Double], progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    val topLevel = Set(docVecs.keys.toSeq:_*)
    val root = new DocTreeNode(topLevel)
    BuildTree(root, threshSteps, progAbort)
  }

  def sampleCloseEdges(numEdgesPerDoc: Int, maxDist:Double): Unit = {
    cc.sampleCloseEdges(numEdgesPerDoc, maxDist)
  }
  
  // --- Main ----
  
  // this entry encapsulates default parameters and usage
  def applyConnectedComponents(root:DocTreeNode, docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    // By default: step down in roughly 0.1 increments
    val threshSteps = List(1, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0)

    // Use edge sampling if docset is large enough, with hard-coded number of samples
    // Random graph connectivity arguments suggest num samples does not need to scale with docset size
    val numDocsWhereSamplingHelpful = 10000
    val numSampledEdgesPerDoc = 200

    if (docVecs.size > numDocsWhereSamplingHelpful)
      sampleCloseEdges(numSampledEdgesPerDoc, 0.8) // use sampled edges if the docset is large
    
    BuildTree(root, threshSteps, progAbort) // actually build the tree!
  }
}


// Use k-means until nodes get small enough, then switch to Connected Components down to leaves
class HybridDocTreeBuilder(protected val docVecs: DocumentSetVectors) {
  
  // Parameters
  val largeNodeSize = 200     // less docs than this, we generate children by finding connected components
  val largeNodeArity = 5      // if we're not finding connected components, we use k-means to split into this many kids
  
  private val km = new KMeansNodeSplitter(docVecs, largeNodeArity)
  private val cc = new ConnectedComponentDocTreeBuilder(docVecs)
  
  private def splitKMeans(node:DocTreeNode, progAbort:ProgressAbortFn) : Unit = {

    if (!progAbort(Progress(0, ClusteringLevel(1)))) { // if we haven't been cancelled...
      
      km.splitNode(node)
  
      // recurse, computing progress along the way
      var i=0
      var denom = node.children.size.toDouble
      node.children foreach { node =>
        splitNode(node, makeNestedProgress(progAbort, i/denom, (i+1)/denom))
        i+=1
      }
      
      progAbort(Progress(1, ClusteringLevel(1)))
    }
  }
  
  // here we do not recurse as the connected component splitter always goes down to the leaves
  private def splitCC(node:DocTreeNode, progAbort:ProgressAbortFn) : Unit = {
    val threshSteps = List(1, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0)
    val tree = cc.buildNodeSubtree(node, threshSteps, progAbort) // actually build the tree!
  }
  
  private def splitNode(node:DocTreeNode, progAbort:ProgressAbortFn) : Unit = {
    if (node.docs.size >= largeNodeSize) {
      splitKMeans(node, progAbort)
      if (node.children.isEmpty)
        splitCC(node, progAbort)    // fall back to splitting w/ connected components if k-means can't split it
    } else {
      //println("---- splitting CC with " +  node.docs.size + " items ----")
      splitCC(node, progAbort)
    }
  }
  
  def BuildTree(root:DocTreeNode,progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {    
    splitNode(root, progAbort)
    root
  } 
  
}