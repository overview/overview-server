package org.overviewproject.tree.orm

import org.specs2.mutable.Specification

class TreeSpec extends Specification {
  private def baseTree = Tree(1L, 2L, "title", 3, "en")
  "Tree.properties" should {
    "have lang by default" in {
      val data = baseTree.creationData.toSeq
      data.length must beEqualTo(1)
      data(0) must beEqualTo("lang" -> "en")
    }

    "add a description" in {
      val s = baseTree.copy(description="foo").creationData.toSeq
      s.length must beEqualTo(2)
      s(1) must beEqualTo("description" -> "foo")
    }

    "add suppliedStopWords" in {
      val s = baseTree.copy(suppliedStopWords="foo").creationData.toSeq
      s.length must beEqualTo(2)
      s(1) must beEqualTo("suppliedStopWords" -> "foo")
    }

    "add importantWords" in {
      val s = baseTree.copy(importantWords="foo").creationData.toSeq
      s.length must beEqualTo(2)
      s(1) must beEqualTo("importantWords" -> "foo")
    }
  }
}
