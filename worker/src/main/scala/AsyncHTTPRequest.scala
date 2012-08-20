/**
 * AsyncHTTPRequest.scala
 * 
 * Thin wrapper on Ning AsyncHTTPClient library, to manage singleton object, and provide callback and akka Future-based interfaces
 *  
 * Overview Project, created August 2012
 * 
 * @author Jonathan Stray
 *
 */

package overview.http

import akka.dispatch.{ExecutionContext,Future,Promise}
import com.ning.http.client.{AsyncHttpClient, AsyncHttpClientConfig, AsyncCompletionHandler, Response => AHCResponse}

// Single object that interfaces to Ning's Async HTTP library
object AsyncHttpRequest {  

  // expose this type so that users don't need to import com.ning.http.client
  type Response =  AHCResponse
  
  private def getHttpConfig = {
    val builder = new AsyncHttpClientConfig.Builder()
    val config = builder
        .setFollowRedirects(true)
        .setCompressionEnabled(true)
        .setAllowPoolingConnection(true)
        .setRequestTimeoutInMs(5 * 60 * 1000) // 5 minutes, to allow for downloading large files
        .build
   config
  }
      
  private lazy val asyncHttpClient = new AsyncHttpClient(getHttpConfig)
  
  // Since AsyncHTTPClient has an executor anyway, allow re-use (if desired) for an Akka Promise execution context
  lazy val executionContext = ExecutionContext.fromExecutor(asyncHttpClient.getConfig().executorService())
  
  // Execute an asynchronous HTTP request, with given callbacks for success and failure
  def apply(url       : String, 
            onSuccess : (Response) => Unit, 
            onFailure : (Throwable) => Unit ) = {
    
    asyncHttpClient.prepareGet(url).execute(
      new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
         onSuccess(response)
         response
        }
        override def onThrowable(t: Throwable) = {
          onFailure(t)
        }
      }) 
  }
  
  // Version that returns a Future[Response]
  def apply(url:String) : Future[Response] = {  
    
    implicit val context = executionContext
    var promise = Promise[Response]()         // uses executionContext derived from asyncClient executor
    
    asyncHttpClient.prepareGet(url).execute(
      new AsyncCompletionHandler[Response]() {
        override def onCompleted(response: Response) = {
          promise.success(response)
          response
        }
        override def onThrowable(t: Throwable) = {
          promise.failure(t)
        }
      })
      
    promise
  }
  
  // You probably shouldn't ever call this :)
  // it will block the thread
  def BlockingHttpRequest(url:String) : String = {
      val f = asyncHttpClient.prepareGet(url).execute()
      f.get().getResponseBody()
  }
}