package org.overviewproject.tree.orm

import org.specs2.mutable.Specification

class TreeSpec extends Specification {
  private def baseTree = Tree(1L, 2L, 3L, 0L, "title", 3, "en")
  "Tree.properties" should {
    "have lang and rootNodeId by default" in {
      val data = baseTree.creationData.toSeq
      data.toMap.get("rootNodeId") must beSome("3")
      data.toMap.get("lang") must beSome("en")
    }

    "add a description" in {
      val s = baseTree.copy(description="foo").creationData.toSeq
      s.toMap.get("description") must beSome("foo")
    }

    "add suppliedStopWords" in {
      val s = baseTree.copy(suppliedStopWords="foo").creationData.toSeq
      s.toMap.get("suppliedStopWords") must beSome("foo")
    }

    "add importantWords" in {
      val s = baseTree.copy(importantWords="foo").creationData.toSeq
      s.toMap.get("importantWords") must beSome("foo")
    }
  }
}
