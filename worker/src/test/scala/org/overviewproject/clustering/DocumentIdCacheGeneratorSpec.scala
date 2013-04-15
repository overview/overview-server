package org.overviewproject.clustering

import scala.collection.mutable.{ ArrayBuffer, Set }
import org.overviewproject.test.Specification
import org.overviewproject.nlp.DocumentVectorTypes.DocumentID
import org.specs2.specification.Scope

class DocumentIdCacheGeneratorSpec extends Specification {

  "DocumentIdCacheGenerator" should {

    trait Setup extends Scope {
      val cacheSize = 5
    }

    trait SingleLeafNode extends Setup {
      val description = "aaa"
      val leafNode = createLeafNode(description, 1l)
    }

    trait SimpleTree extends Setup {
      val documentIds = Set[DocumentID](3l, 2l, 1l)
      val descriptions = Seq("aaa", "bbb", "ccc")

      val root = new DocTreeNode(documentIds)
      documentIds.foreach { i =>
        val n = createLeafNode(descriptions(i.toInt - 1), i)
        root.children.add(n)
      }
    }

    trait LargeTree extends Setup {
      def createParentNode(children: Seq[DocTreeNode]): DocTreeNode = {
        val allDocs = children.flatMap(_.docs)
        val n = new DocTreeNode(Set(allDocs: _*))
        n.children ++= children
        n
      }

      val idsWithDescriptions = Seq.fill(100)((scala.util.Random.nextLong, scala.util.Random.nextLong.toString))
      val leafNodes = idsWithDescriptions.map(l => createLeafNode(l._2, l._1))
      val level1Nodes = leafNodes.grouped(10).map { createParentNode }
      val level2Nodes = level1Nodes.grouped(5).map { createParentNode }
      val root = createParentNode(level2Nodes.toSeq)

      val expectedCache = idsWithDescriptions.sortBy(x => (x._2, x._1)).map(_._1).take(10)
    }

    trait EqualDescriptions extends Setup {
      val documentIds = Set[DocumentID](10l, 4l, 7l, 3l)

      val root = new DocTreeNode(documentIds)
      documentIds.foreach { i =>
        val n = createLeafNode("same for all", i)
        root.children.add(n)
      }
    }
    
    "return the cache for a leaf node, consisting of one id" in new SingleLeafNode {
      DocumentIdCacheGenerator.createCache(leafNode)

      leafNode.documentIdCache.numberOfDocuments must be equalTo (1)
      leafNode.documentIdCache.documentIds(0) must be equalTo (1l)
    }

    "return the cache, sorted by document description" in new SimpleTree {
      DocumentIdCacheGenerator.createCache(root)
      root.documentIdCache.numberOfDocuments must be equalTo (3)
      root.documentIdCache.documentIds.toSeq must be equalTo (Seq(1l, 2l, 3l))
    }

    "return a cache of size 10" in new LargeTree {
      DocumentIdCacheGenerator.createCache(root)
      root.documentIdCache.numberOfDocuments must be equalTo (idsWithDescriptions.size)

      root.documentIdCache.documentIds.toSeq must be equalTo (expectedCache)
    }

    "sets the cache for intermediate nodes" in new LargeTree {
      DocumentIdCacheGenerator.createCache(root)
      
      val childCaches = idsWithDescriptions.grouped(50).map(_.sortBy(_._2).map(_._1)).map(_.take(10)).toSeq
      
      val caches = root.children.map(_.documentIdCache.documentIds.toSeq)
      caches must haveTheSameElementsAs(childCaches)
    }
    
    "sort by id if descriptions are the same" in new EqualDescriptions {
      DocumentIdCacheGenerator.createCache(root)
      val ids = root.documentIdCache.documentIds
      ids must be equalTo (ids.sorted)
    }
  }

  private def createLeafNode(description: String, documentId: DocumentID): DocTreeNode = {
    val leaf = new DocTreeNode(Set(documentId))
    leaf.description = description
    leaf
  }
}