package org.overviewproject.blobstorage

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import java.io.ByteArrayInputStream
import play.api.libs.iteratee.{ Enumerator, Iteratee }

trait StrategySpecHelper {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)
  def byteArrayInputStream(bytes: Array[Byte]) = new ByteArrayInputStream(bytes)
  def utf8InputStream(string: String) = byteArrayInputStream(string.getBytes("utf-8"))
  def consume(enumerator: Enumerator[Array[Byte]]): Array[Byte] = {
    val f = Iteratee.consume[Array[Byte]]()
    await(Iteratee.flatten(enumerator(f)).run)
  }

}