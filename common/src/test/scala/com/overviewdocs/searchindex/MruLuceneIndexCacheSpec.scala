package com.overviewdocs.searchindex

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.util.concurrent.{Executor,RejectedExecutionException}
import scala.collection.mutable
import scala.concurrent.{Await,ExecutionContext,Future}
import scala.concurrent.duration.Duration

class MruLuceneIndexCacheSpec extends Specification with Mockito {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  trait BaseScope extends Scope {
    // Make a synchronous ExecutionContext -- for easy tests
    val executor = new Executor {
      override def execute(command: Runnable): Unit = {
        try {
          command.run
        } catch {
          case e: Exception => throw new RejectedExecutionException(e)
        }
      }
    }

    val indexes = Array(
      smartMock[DocumentSetLuceneIndex],
      smartMock[DocumentSetLuceneIndex],
      smartMock[DocumentSetLuceneIndex],
      smartMock[DocumentSetLuceneIndex]
    )

    val loadCalls = mutable.ArrayBuffer.empty[Long]
    def loads: String = loadCalls.mkString("")

    val cache = new MruLuceneIndexCache(
      (id: Long) => { loadCalls.+=(id); indexes(id.toInt) },
      2, // 2 concurrent indexes
      ExecutionContext.fromExecutor(executor)
    )
  }

  "MruLuceneIndexCache" should {
    "#get" should {
      "load an index" in new BaseScope {
        await(cache.get(1L)) must beEqualTo(indexes(1))
        loads must beEqualTo("1")
      }

      "reuse a loaded index" in new BaseScope {
        await(cache.get(1L))
        await(cache.get(1L))
        loads must beEqualTo("1")
      }

      "load a second index" in new BaseScope {
        await(cache.get(1L))
        await(cache.get(2L)) must beEqualTo(indexes(2))
        loads must beEqualTo("12")
      }

      "reuse an index after using a second one" in new BaseScope {
        await(cache.get(1L))
        await(cache.get(2L))
        await(cache.get(1L))
        loads must beEqualTo("12")
      }

      "close an index when nConcurrentDocumentSets is exceeded" in new BaseScope {
        await(cache.get(1L))
        Thread.sleep(1)
        await(cache.get(2L))
        Thread.sleep(1)
        await(cache.get(3L)) must beEqualTo(indexes(3))
        there was one(indexes(1)).close
        await(cache.get(1L)) must beEqualTo(indexes(1))
        there was one(indexes(2)).close
        loads must beEqualTo("1231")
      }

      "close the least-recently-used index, even if it wasn't opened first" in new BaseScope {
        await(cache.get(1L))
        Thread.sleep(1)
        await(cache.get(2L))
        Thread.sleep(1)
        await(cache.get(1L))
        await(cache.get(3L))
        there was one(indexes(2)).close
      }
    }
  }
}
