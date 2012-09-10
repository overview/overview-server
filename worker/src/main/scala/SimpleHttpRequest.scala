
package overview.http

import com.ning.http.client.{ AsyncHttpClient, AsyncHttpClientConfig, Realm, Response }
import com.ning.http.client.Realm.AuthScheme

object SimpleHttpRequest {

  val builder = new AsyncHttpClientConfig.Builder
  val config = builder.setFollowRedirects(false)
    .setCompressionEnabled(true)
    .setAllowPoolingConnection(true)
    .setRequestTimeoutInMs(5 * 60 * 1000)
    .build

  val asyncHttpClient = new AsyncHttpClient(config)

  def apply(resource: DocumentAtURL): Response = {
    val response = resource match {
      case r: DocumentAtURL with BasicAuth => {
        val realm = new Realm.RealmBuilder()
          .setPrincipal(r.username)
          .setPassword(r.password)
          .setUsePreemptiveAuth(true)
          .setScheme(AuthScheme.BASIC)
          .build();

        asyncHttpClient.prepareGet(resource.textURL).setRealm(realm).execute()
      }
      case _ =>  asyncHttpClient.prepareGet(resource.textURL).execute()
    }

    response.get
  }
}
