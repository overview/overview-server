package com.overviewdocs.models

import org.specs2.mutable.Specification

import com.overviewdocs.test.factories.{PodoFactory=>factory}

class DocumentProcessingErrorSpec extends Specification {
  "DocumentProcessingError" should {
    "return an HTTP status code" in {
      val dpe = factory.documentProcessingError(statusCode=Some(400))
      dpe.statusMessage must beEqualTo("HTTP 400 Bad Request")
    }

    "return a message if there is no status code" in {
      val dpe = factory.documentProcessingError(message="a-message", statusCode=None)
      dpe.statusMessage must beEqualTo("a-message")
    }
  }
}
