package models

import javax.inject.Inject
import scala.concurrent.Future
import play.api.Configuration
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{WSClient,WSResponse}

class MailChimp @Inject() (configuration: Configuration, wsClient: WSClient) {
  private val MailChimpConfigMock = "mailchimp.mock"
  private val MailChimpConfigListId = "mailchimp.list_id"
  private val MailChimpConfigApiKey = "mailchimp.api_key"
    
  private val ApiKeyFormat = "[^-]*-(.*)".r
  private val MailChimpApiEndPoint = "http://%s.api.mailchimp.com/1.3/"
  private def MailChimpApi(dataCenter: String): String = MailChimpApiEndPoint.format(dataCenter)
  private val ListSubscribe = "?method=listSubscribe"

  def subscribe(email: String): Option[Future[WSResponse]] = {
    for {
      mock <- configuration.getBoolean(MailChimpConfigMock)
      if (!mock)
      listId <- configuration.getString(MailChimpConfigListId)
      apiKey <- configuration.getString(MailChimpConfigApiKey)
      listSubscribeMethod <- createListSubscribeMethod(apiKey)
    } yield {
      val params = Map(
        "apikey" -> Seq(apiKey),
        "id" -> Seq(listId),
        "email_address" -> Seq(email),
        "double_optin" -> Seq("false"))
      wsClient.url(listSubscribeMethod).post(params)
    }
  }

  private def createListSubscribeMethod(apiKey: String): Option[String] =
    for (ApiKeyFormat(dataCenter) <- ApiKeyFormat.findFirstIn(apiKey))
      yield MailChimpApi(dataCenter) + ListSubscribe
}
