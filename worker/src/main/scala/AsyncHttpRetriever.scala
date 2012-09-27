
package overview.http

import akka.dispatch.ExecutionContext
import com.ning.http.client.Response

  
trait AsyncHttpRetriever {

  def request(resource: DocumentAtURL, onSuccess: Response => Unit, onFailure: Throwable => Unit): Unit
  def blockingHttpRequest(url: String): String
  
  val executionContext: ExecutionContext
  
}
