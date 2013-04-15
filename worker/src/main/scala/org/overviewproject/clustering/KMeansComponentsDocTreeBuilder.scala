/**
 * KMeansComponentsDocTreeBuilder.scala
 * Break documents into connected components that are very similar,
 * then cluster the components using recursive variable k-means
 * 
 * Overview Project, created March 2013
 *
 * @author Jonathan Stray
 *
 */

package org.overviewproject.clustering
import scala.collection.mutable.{Set, ArrayBuffer}
import org.overviewproject.util.DocumentSetCreationJobStateDescription.ClusteringLevel
import org.overviewproject.util.Progress.{ Progress, ProgressAbortFn, makeNestedProgress, NoProgressReporting }
import org.overviewproject.util.ToMutableSet._
import org.overviewproject.util.Logger
import org.overviewproject.nlp.DocumentVectorTypes._
import org.overviewproject.nlp.IterativeKMeansDocuments


class KMeansComponentsDocTreeBuilder(docVecs: DocumentSetVectors, k:Int) { 
  private val kmComponents = new IterativeKMeansDocumentComponents(docVecs)
  private val kmDocs = new IterativeKMeansDocuments(docVecs)
    
  // ---- Config ----
  val minComponentFractionForOwnNode = 0.2  // when component is at least this fraction of docs in node, it gets its own child
  val minSplitSize = 2   // keep breaking into clusters until <= this many docs in a node
  val maxDepth = 10      // ...or we reach depth limit

  // Create a new node from a set of components 
  private def newNode(components:Iterable[DocumentComponent]) : DocTreeNode = {
    val componentsSet = components.toMutableSet  // first convert to set, then use that to get docs, as the Iterable may be slow (e.g. a view)
    val docs = Set(componentsSet.view.toSeq.flatMap(_.docs):_*)
    val n = new DocTreeNode(docs)
    n.components = componentsSet
    n
  }
  
  // Cluster the components within a single node, like this:
  //  - if there are one or zero components, split the docs via k-means
  //  - if there are any sufficiently large components, they each get their own node
  //  - all other components are grouped into new child nodes, via k-means
  private def splitNode(node:DocTreeNode) : Unit = {
    val components = node.components
    
    if (components.size < 2) {
      // Zero or one components, we are clustering docs directly from this point down 
      val stableDocs = node.docs.toArray.sorted   // sort documentIDs, to ensure consistent input to kmeans
      val assignments = kmDocs(stableDocs, k)
      for (i <- 0 until k) { 
        val docsInNode = kmDocs.elementsInCluster(i, stableDocs, assignments)  // document IDs assigned to cluster i, lazily produced
        if (docsInNode.size > 0)
          node.children += new DocTreeNode(Set(docsInNode:_*))
      }
      
    } else {
      // Two or more components      
      // First split out all sufficiently large components into their own children. Count these against the arity.
      val smallComponents = ArrayBuffer[DocumentComponent]()
      var maxArity = k
      
      components foreach { component => 
        if (component.nDocs > minComponentFractionForOwnNode * node.docs.size) {
          val child = new DocTreeNode(Set(component.docs:_*))
          // child.description = "B "  // component split off by itself
          node.children += child
          maxArity -= 1
        } else {
          smallComponents += component
        }
      } 

      // Now divide remaining remaining components (if any) among child nodes, that is, cluster the components 
      if (smallComponents.size > 0) {
        if (maxArity <= 1) {
          node.children += newNode(smallComponents) // we've already used at least k-1 nodes for big components, all the rest go in one node
        } else {
          val assignments = kmComponents(smallComponents, maxArity)
          for (i <- 0 until maxArity) { 
            val componentsInNode = kmComponents.elementsInCluster(i, smallComponents, assignments)
            if (componentsInNode.size > 0) {
              val child = newNode(componentsInNode)
              // child.description = if (componentsInNode.size > 1) "M " else "C "   // "Merged component" vs "individual Component"
              node.children += child
            }
          }
        }       
      }
    }
       
    if (node.children.size == 1)    // if all docs went into single node, make this a leaf, we are done
      node.children.clear           // (either "really" only one cluster, or clustering alg problem, but let's never infinite loop)
  }
  
  def makeALeafForEachDoc(node:DocTreeNode) = {
    if (node.docs.size > 1)
      node.docs foreach { id => 
        node.children += new DocTreeNode(Set(id))
    }
  }  
  
  // Split the given node, which must contain the documents from all given components.
  private def splitNode(node:DocTreeNode, level:Integer, progAbort:ProgressAbortFn) : Unit = {
    
    if (!progAbort(Progress(0, ClusteringLevel(2)))) { // if we haven't been cancelled...
        
      if ((level < maxDepth) && (node.docs.size >= minSplitSize)) {
         
        splitNode(node)
        
        if (node.children.isEmpty) {
          // we couldn't split it, just put each doc in a leaf
          makeALeafForEachDoc(node)
        } else {
          // recurse, computing progress along the way
          var i=0
          var denom = node.children.size.toDouble
          node.children foreach { node =>
            splitNode(node, level+1, makeNestedProgress(progAbort, i/denom, (i+1)/denom))
            i+=1
          }
        }
      } else {
        // smaller nodes, or max depth reached, produce a leaf for each doc
        if (node.docs.size > 1) 
          node.children = node.docs.map(item => new DocTreeNode(Set(item)))
      }
      
      node.components.clear     // we are done with those components, if any, so empty that list
      progAbort(Progress(1, ClusteringLevel(2)))
    }
  }
  
  private def makeComponents(docs:Iterable[DocumentID]) : Set[DocumentComponent] = {
    val threshold = 0.5                       // docs more similar than this will be in same component
    val numDocsWhereSamplingHelpful = 5000
    val numSampledEdgesPerDoc = 500

    val cc = new ConnectedComponentsDocuments(docVecs)
    
    if (docVecs.size > numDocsWhereSamplingHelpful)
      cc.sampleCloseEdges(numSampledEdgesPerDoc, threshold) // use sampled edges if the docset is large
      
    val components = Set[DocumentComponent]()
    cc.foreachComponent(docs, threshold) { 
      components += new DocumentComponent(_, docVecs)
    }
    
    Logger.info("Found " +  components.size + " connected components at threshold " + threshold)
    components
  }
  
  // ---- Main ----
  
  def BuildTree(root:DocTreeNode, progAbort: ProgressAbortFn = NoProgressReporting): DocTreeNode = {
    
    progAbort(Progress(0, ClusteringLevel(1)))
    Logger.logExecutionTime("Found connected components") {
      root.components = makeComponents(root.docs)
    }
    
    if (!progAbort(Progress(0.2, ClusteringLevel(2)))) { // if we haven't been cancelled...
      Logger.logExecutionTime("Clustered components") {
        splitNode(root, 1, makeNestedProgress(progAbort, 0.2, 1.0))   // root is level 0, so first split is level 1
      }
    }
    
    root
  }
}