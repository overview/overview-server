/**
 * SmallNodeBundlerSpec.scala
 * 
 * Overview Project, created November 2012
 * @author Jonathan Stray
 * 
 */

import scala.collection.mutable.Set
import org.overviewproject.clustering.ClusterTypes.DocumentID
import org.overviewproject.clustering.DocTreeNode
import org.specs2.mutable.Specification
import org.specs2.specification._
import org.overviewproject.clustering.SmallNodeBundler

class SmallNodeBundlerSpec extends Specification {

  "SmallNodeBundler" should {
        
    def makeNodeWithKids(totalDocs:Int, docsPerKid:Int) : DocTreeNode = {
      var allDocs = Set[DocumentID](1.asInstanceOf[DocumentID] until totalDocs.asInstanceOf[DocumentID]: _*)
      val root = new DocTreeNode(allDocs)
      
      while (!allDocs.isEmpty) {
        root.children += new DocTreeNode(allDocs.take(docsPerKid))
        allDocs = allDocs.drop(docsPerKid)
      }

      root
    }
    
    "bundle a node" in {
      var n = makeNodeWithKids(100, 10)   // ten children, each with 10 nodes
      
      val bundler = new SmallNodeBundler(5)
      bundler.cleanTree(n)  
      n.children.size must beEqualTo(5)
      (n.children find { _.description == "(other)" }).isDefined must beTrue // we must have made an "other" node
    }
    
    /* disable because we do not recursively bunder "(other)" nodes right now
    
    def maxTreeArity(n:DocTreeNode) : Int  = {
      n.children.foldLeft (n.children.size) { (maxSoFar,child) => math.max(maxSoFar, maxTreeArity(child)) }
    }

    "bundle a tree" in {
      // Make a tree three levels deep, where each node has 20 children of 10 docs each -- 8000 nodes total
      // (it's not a valid document tree, because children don't have strict subsets of parent docs, but that does not matter here)
      var root = makeNodeWithKids(200, 10)  
      root.children foreach { child => 
        child.children = makeNodeWithKids(200,10).children
        child.children foreach { child2 => 
          child2.children = makeNodeWithKids(200,10).children
        }
      }

      // If we force no more than 5 children of each node, then the top level "other" nodes will need to be split
      SmallNodeBundler.limitTreeMaxChildren(root, 5)
      
      maxTreeArity(root) must beEqualTo(5)
    } */
  } 
}