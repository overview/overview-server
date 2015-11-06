package com.overviewdocs.jobhandler.filegroup

import org.specs2.mutable.Specification

class TaskIdPoolSpec extends Specification {
  "TaskIdPool" should {
    "return 1 on first call" in {
      TaskIdPool().acquireId must beEqualTo(1)
    }

    "return 2 on second call" in {
      val pool = TaskIdPool()
      pool.acquireId
      pool.acquireId must beEqualTo(2)
    }

    "reuse 1 after it is released" in {
      val pool = TaskIdPool()
      pool.acquireId
      pool.releaseId(1)
      pool.acquireId must beEqualTo(1)
    }

    "reuse 1, even if another ID is being used after it" in {
      val pool = TaskIdPool()
      pool.acquireId
      pool.acquireId
      pool.releaseId(1)
      pool.acquireId must beEqualTo(1)
    }

    "reuse 1, even if 1 is still running" in {
      val pool = TaskIdPool()
      pool.acquireId
      pool.acquireId
      pool.releaseId(2)
      pool.acquireId must beEqualTo(2)
    }
  }
}
