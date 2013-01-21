/**
 * GenerateClusters.scala
 * Given a set of document vectors, return a tree hierarchical tree of clusters
 * Based on an algorithm by Stephen Ingram
 *
 * Overview Project, created July 2012
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering

import scala.collection.mutable.Set
import scala.util.control.Breaks._
import overview.util.DocumentSetCreationJobStateDescription.ClusteringLevel
import overview.util.Progress.{ NoProgressReporting, Progress, ProgressAbortFn }
import org.overviewproject.clustering.ClusterTypes._


class ConnectedComponentDocTreeBuilder(protected val docVecs: DocumentSetVectors, protected val distanceFn: DocumentDistanceFn) {

  private var sampledEdges = new SampledEdges

  // Produces all docs reachable from a given start doc, given thresh
  // Unoptimized implementation, scans through all possible edges (N^2 total)
  private def allReachableDocs(thresh: Double, thisDoc: DocumentID, otherDocs: Set[DocumentID]): Iterable[DocumentID] = {
    for (
      otherDoc <- otherDocs;
      if distanceFn(docVecs(thisDoc), docVecs(otherDoc)) <= thresh
    ) yield otherDoc
  }

  // Same logic as above, but only looks through edges stored in sampledEdges
  private def sampledReachableDocs(thresh: Double, thisDoc: DocumentID, otherDocs: Set[DocumentID]): Iterable[DocumentID] = {
    val g = sampledEdges.get(thisDoc)
    if (g.isDefined) {
      for (
        (otherDoc, distance) <- g.get if otherDocs.contains(otherDoc) if distance <= thresh
      ) yield otherDoc
    } else {
      Nil
    }
  }

  // Returns an edge walking function suitable for ConnectedComponents, using the sampled edge set if we have it
  private def createEdgeEnumerator(thresh: Double) = {
    if (!sampledEdges.isEmpty)
      (doc: DocumentID, docSet: Set[DocumentID]) => sampledReachableDocs(thresh, doc, docSet)
    else
      (doc: DocumentID, docSet: Set[DocumentID]) => allReachableDocs(thresh, doc, docSet)
  }

  // Expand out the nodes of the tree by thresholding the documents in each and seeing if they split into components
  // Returns new set of leaf nodes
  private def ExpandTree(currentLeaves: List[DocTreeNode], thresh: Double) = {
    var nextLeaves = List[DocTreeNode]()

    for (node <- currentLeaves) {

      val childComponents = ConnectedComponents.AllComponents[DocumentID](node.docs, createEdgeEnumerator(thresh))

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

  // Steps distance thresh along given sequence. First step must always be 1 = full graph, 0 must always be last = leaves
  def BuildTree(threshSteps: Seq[Double],
    progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    require(threshSteps.head == 1.0)
    require(threshSteps.last == 0.0)
    require(threshSteps.forall(step => step >= 0 && step <= 1.0))
    val numSteps: Double = threshSteps.size

    // root thresh=1.0 is one node with all documents
    progAbort(Progress(0, ClusteringLevel(1)))
    var topLevel = Set(docVecs.keys.toSeq:_*)
    val root = new DocTreeNode(topLevel)

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
    root
  }

  def sampleCloseEdges(numEdgesPerDoc: Int): Unit = {
    sampledEdges = new EdgeSampler(docVecs, distanceFn).edges(numEdgesPerDoc)
  }

}

class KMeansDocTreeBuilder(protected val docVecs: DocumentSetVectors, protected val k:Int) {

  val stopSize = 16   // keep breaking into clusters until <= 16 docs in a node
  
  private val km = new KMeansDocuments(docVecs)
  km.seedClusterSize = 1
  km.maxIterations = 15
  
  private def splitNode(node:DocTreeNode) : Unit = {
    
    if (node.docs.size > stopSize) {
      // split larger nodes into smaller ones by clustering
      val assignments = km(node.docs, k)
      for (i <- 0 until k) { 
        val docsInThisCluster = assignments.view.filter(_._2 == i).map(_._1)  // document IDs assigned to cluster i, lazily produced
        if (docsInThisCluster.size > 0)
          node.children += new DocTreeNode(Set(docsInThisCluster:_*))
      }
      node.children foreach splitNode
    } else {
      // smaller nodes, produce a leaf for each doc
      if (node.docs.size > 1) 
        node.children = node.docs.map(item => new DocTreeNode(Set(item)))
    }
  }
  
  def BuildTree(progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    // is one node with all documents
    var topLevel = Set(docVecs.keys.toSeq:_*)
    val root = new DocTreeNode(topLevel)
    
    splitNode(root)
    println("----- Sizes of root children: " + root.children.map(_.docs.size).mkString(",") + " -----")
    
    root
  }
}

// Given a set of document vectors, generate a tree of nodes and their descriptions
// This is where all of the hard-coded algorithmic constants live
object BuildDocTree {

  def applyConnectedComponents(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    // By default: cosine distance, and step down in roughly 0.1 increments
    val distanceFn = (a:DocumentVector,b:DocumentVector) => DistanceFn.CosineDistance(a,b) // can't use CosineDistance because of method overloading :(
    val threshSteps = List(1, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0)

    // Use edge sampling if docset is large enough, with hard-coded number of samples
    // Random graph connectivity arguments suggest num samples does not need to scale with docset size
    val numDocsWhereSamplingHelpful = 10000
    val numSampledEdgesPerDoc = 200

    // Maximum arity of the tree (smallest nodes will be bundled)
    val maxChildrenPerNode = 5

    val builder = new ConnectedComponentDocTreeBuilder(docVecs, distanceFn)
    if (docVecs.size > numDocsWhereSamplingHelpful)
      builder.sampleCloseEdges(numSampledEdgesPerDoc) // use sampled edges if the docset is large
    val tree = builder.BuildTree(threshSteps, progAbort) // actually build the tree!
    new TreeLabeler(docVecs).labelNode(tree) // create a descriptive label for each node
    ThresholdTreeCleaner(tree) // prune the tree

    DocumentIdCacheGenerator.createCache(tree)

    tree
  }
  
  def applyKMeans(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {

    val arity = 5

    val builder = new KMeansDocTreeBuilder(docVecs, arity)
    
    val tree = builder.BuildTree(progAbort) // actually build the tree!
    new TreeLabeler(docVecs).labelNode(tree) // create a descriptive label for each node
    ThresholdTreeCleaner(tree) // prune the tree

    DocumentIdCacheGenerator.createCache(tree)

    tree
  }
    
  def apply(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    applyKMeans(docVecs, progAbort)
    //applyConnectedComponents(docVecs, progAbort)
  }
 
}
