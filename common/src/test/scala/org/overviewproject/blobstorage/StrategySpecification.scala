package org.overviewproject.blobstorage

import java.io.ByteArrayInputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

class StrategySpecification
  extends Specification
  with Mockito
{
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)
  def byteArrayInputStream(bytes: Array[Byte]) = new ByteArrayInputStream(bytes)
  def utf8InputStream(string: String) = byteArrayInputStream(string.getBytes("utf-8"))
  def consume(enumerator: Enumerator[Array[Byte]]): Array[Byte] = {
    val f = Iteratee.consume[Array[Byte]]()
    await(Iteratee.flatten(enumerator(f)).run)
  }

  trait BaseScope extends Scope {
  }
}
