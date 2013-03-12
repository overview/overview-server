package models

import scala.concurrent.Future
import play.api.Play
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.ws.{ Response, WS }

object MailChimp {
  private val MailChimpConfigMock = "mailchimp.mock"
  private val MailChimpConfigListId = "mailchimp.list_id"
  private val MailChimpConfigApiKey = "mailchimp.api_key"
    
  private val ApiKeyFormat = "[^-]*-(.*)".r
  private val MailChimpApiEndPoint = "http://%s.api.mailchimp.com/1.3/"
  private def MailChimpApi(dataCenter: String): String = MailChimpApiEndPoint.format(dataCenter)
  private val ListSubscribe = "?method=listSubscribe"

  def subscribe(email: String): Option[Future[Response]] =
    for {
      mock <- getBooleanConfigValue(MailChimpConfigMock)
      if (!mock)
      listId <- getStringConfigValue(MailChimpConfigListId)
      apiKey <- getStringConfigValue(MailChimpConfigApiKey)
      listSubscribeMethod <- createListSubscribeMethod(apiKey)
    } yield {
      val params = Map(
        "apikey" -> Seq(apiKey),
        "id" -> Seq(listId),
        "email_address" -> Seq(email),
        "double_optin" -> Seq("false"))
      WS.url(listSubscribeMethod).post(params)
    }

  private def createListSubscribeMethod(apiKey: String): Option[String] =
    for (ApiKeyFormat(dataCenter) <- ApiKeyFormat.findFirstIn(apiKey))
      yield MailChimpApi(dataCenter) + ListSubscribe

  private def getBooleanConfigValue(key: String): Option[Boolean] = Play.current.configuration.getBoolean(key)
  private def getStringConfigValue(key: String): Option[String] = Play.current.configuration.getString(key)
}