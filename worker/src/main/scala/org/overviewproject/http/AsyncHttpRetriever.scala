
package org.overviewproject.http

import scala.concurrent.ExecutionContext
import com.ning.http.client.Response

trait AsyncHttpRetriever {

  def request(resource: DocumentAtURL, onSuccess: Response => Unit, onFailure: Throwable => Unit): Unit
  def blockingHttpRequest(url: String): String
  
  val executionContext: ExecutionContext
  
}
