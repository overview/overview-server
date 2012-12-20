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

// Encapsulates document-document distance function. Returns in range 0 == identical to 1 == unrelated
object DistanceFn {

  // sparse dot product on two term->float maps
  // Not written in very functional style, as DocumentVector uses an awkward representation as arrays for space reasons
  // Basic intersection of sorted lists algorithm
  private def SparseDot(a: DocumentVector, b: DocumentVector): Double = {
    var a_idx = 0
    var b_idx = 0
    var dot = 0.0

    while (a_idx < a.length && b_idx < b.length) {
      val a_term = a.terms(a_idx)
      val b_term = b.terms(b_idx)

      if (a_term < b_term) {
        a_idx += 1
      } else if (b_term < a_term) {
        b_idx += 1
      } else {
        dot += a.weights(a_idx).toDouble * b.weights(b_idx).toDouble
        a_idx += 1
        b_idx += 1
      }
    }

    dot
  }

  // Document distance computation. Returns 1 - similarity, where similarity is cosine of normalized vectors
  def CosineDistance(a: DocumentVector, b: DocumentVector) = {
    1.0 - SparseDot(a, b)
  }
}

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
    var topLevel = Set(docVecs.keys.toArray: _*)
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

// Add node labeling to the Tree Builder
class LabellingDocTreeBuilder(docVecs: DocumentSetVectors, distanceFn: DocumentDistanceFn)
  extends ConnectedComponentDocTreeBuilder(docVecs, distanceFn) {

  // Turn a set of document vectors into a descriptive string. Takes top weighted terms, separates by commas
  private def makeDescription(vec: DocumentVectorMap): String = {
    val maxTerms = 15
    vec.toList.sortWith(_._2 > _._2).take(maxTerms).map(_._1).map(docVecs.stringTable.idToString(_)).mkString(", ")
  }

  // Sparse vector sum, used for computing node descriptions
  private def accumDocumentVector(acc: DocumentVectorMap, v: DocumentVectorMap): Unit = {
    v foreach {
      case (id, weight) => acc.update(id, acc.getOrElse(id, 0f) + weight)
    }
  }

  // Create a descriptive string for each node, by taking the sum of all document vectors in that node.
  // Building all descriptions at once allows a lot of re-use of sub-sums.
  def labelNode(node: DocTreeNode): DocumentVectorMap = {

    if (node.docs.size == 1) {
      require(node.children.isEmpty)
      val vec = DocumentVectorMap(docVecs(node.docs.head)) // get document vector corresponding to our single document ID
      node.description = makeDescription(vec)
      vec
    } else {
      var vec = DocumentVectorMap()
      for (child <- node.children) {
        accumDocumentVector(vec, labelNode(child)) // sum the document vectors of all child nodes
      }
      node.description = makeDescription(vec)
      vec
    }
  }
}

// Given a set of document vectors, generate a tree of nodes and their descriptions
// This is where all of the hard-coded algorithmic constants live
object BuildDocTree {

  def apply(docVecs: DocumentSetVectors, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    // By default: cosine distance, and step down in 0.1 increments
    val distanceFn = DistanceFn.CosineDistance _
    val threshSteps = List(1, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0) // can't do (1.0 to 0.1 by -0.1) cause last val must be exactly 0

    // Use edge sampling if docset is large enough, with hard-coded number of samples
    // Random graph connectivity arguments suggest num samples does not need to scale with docset size
    val numDocsWhereSamplingHelpful = 10000
    val numSampledEdgesPerDoc = 200

    // Maximum arity of the tree (smallest nodes will be bundled)
    val maxChildrenPerNode = 5

    val builder = new LabellingDocTreeBuilder(docVecs, distanceFn)
    if (docVecs.size > numDocsWhereSamplingHelpful)
      builder.sampleCloseEdges(numSampledEdgesPerDoc) // use sampled edges if the docset is large
    val tree = builder.BuildTree(threshSteps, progAbort) // actually build the tree!
    builder.labelNode(tree) // create a descriptive label for each node
    ThresholdTreeCleaner(tree) // prune the tree

    DocumentIdCacheGenerator.createCache(tree)

    tree
  }

}
