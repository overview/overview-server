package org.overviewproject.clustering

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import org.overviewproject.nlp.DocumentVectorTypes.DocumentID

object DocumentIdCacheGenerator {

  private type CacheElement = (DocumentID, String)
  private type Cache = Seq[CacheElement]

  private val CacheSize = 10
  
  def createCache(node: DocTreeNode) {
    val documentIds = documentIdCacheWithDescriptions(node).map(_._1).toArray

    node.documentIdCache = new DocumentIdCache(node.docs.size, documentIds)
  }

  private def documentIdCacheWithDescriptions(node: DocTreeNode): Cache = {

    if (node.children.isEmpty) {
      node.documentIdCache = new DocumentIdCache(node.docs.size, node.docs.toArray)
      node.docs.map((_, node.description)).toSeq
    }
    else {
      val childCaches = node.children.map(documentIdCacheWithDescriptions)
      val cacheWithDescriptions = childCaches.foldLeft(Seq[CacheElement]())((a, b) => cacheMerge(a, b, Nil))
      val cache = new Array[DocumentID](cacheWithDescriptions.size)
      cacheWithDescriptions.map(_._1).copyToArray(cache, 0, cache.size)
      node.documentIdCache = new DocumentIdCache(node.docs.size, cache)
      
      cacheWithDescriptions
    }
  }

  @tailrec
  private def cacheMerge(cache1: Cache, cache2: Cache, merge: Cache): Cache =
    if (cache1.isEmpty) merge ++ cache2 take(CacheSize)
    else if (cache2.isEmpty) merge ++ cache1 take(CacheSize)
    else {
      val caches = Seq(cache1, cache2).sortBy(c => (c.head._2, c.head._1))
      cacheMerge(caches(0).tail, caches(1), merge :+ caches(0).head)
    }

}