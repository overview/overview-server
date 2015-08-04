package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.Future
import scala.util.Try

/**
 * Ensure that synchronous code returns a failed [[Future]] instead of
 * an [[Exception]]
 */
object AsFuture {
  def apply[T](f: => T): Future[T] = Future.fromTry(Try(f))
}