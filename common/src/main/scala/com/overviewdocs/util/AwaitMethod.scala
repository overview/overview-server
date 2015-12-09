package com.overviewdocs.util

import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.duration.Duration

trait AwaitMethod {
  protected def await[T](future: Future[T]): T = blocking(Await.result(future, Duration.Inf))
}
