package org.overviewproject.test

import org.overviewproject.http.SimpleResponse

case class TestSimpleResponse(override val status: Int, override val body: String, simpleHeaders: Map[String, String] = Map()) extends SimpleResponse {
  override def headers(name: String): Seq[String] = simpleHeaders.get(name) match {
    case Some(value) => Seq(value)
    case _ => Seq.empty
  }
}
