package models

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.{WSClient,WSResponse}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success,Failure}

import com.overviewdocs.util.Logger

/** Subscribes users to the configured mailing list.
  *
  * If mailchimp.list_id="" or mailchimp.api_key="", every subscribe is a no-op.
  */
class MailChimp @Inject() (configuration: Configuration, wsClient: WSClient) {
  private val MailChimpConfigListId = "mailchimp.list_id"
  private val MailChimpConfigApiKey = "mailchimp.api_key"
    
  private val ApiKeyFormat = "[^-]*-(.*)".r
  private val MailChimpApiEndPoint = "http://%s.api.mailchimp.com/1.3/"
  private def MailChimpApi(dataCenter: String): String = MailChimpApiEndPoint.format(dataCenter)
  private val ListSubscribe = "?method=listSubscribe"

  private val logger = Logger.forClass(getClass)

  private val listId: String = configuration.get[String](MailChimpConfigListId)
  private val apiKey: String = configuration.get[String](MailChimpConfigApiKey)
  private val maybeSubscribeUrl: Option[String] = if (listId.nonEmpty && apiKey.nonEmpty) {
    apiKey match {
      case ApiKeyFormat(dataCenter) => Some(MailChimpApi(dataCenter) + ListSubscribe)
      case _ => throw new RuntimeException(s"Invalid MailChimp API key: `${apiKey}`")
    }
  } else {
    None
  }

  def subscribeInBackground(email: String): Unit = {
    maybeSubscribeUrl match {
      case None => {
        logger.info("MailChimp is not configured: not subscribing {} to MailChimp list", email)
      }
      case Some(subscribeUrl) => {
        logger.info("Subscribing {} to MailChimp list…", email)

        val params = Map(
          "apikey" -> Seq(apiKey),
          "id" -> Seq(listId),
          "email_address" -> Seq(email),
          "double_optin" -> Seq("false")
        )
        wsClient.url(subscribeUrl).post(params)
          .andThen {
            case Failure(ex) => logger.warn("Error subscribing {} to MailChimp", email, ex)
            case Success(response) => {
              if (response.status / 100 == 2) {
                logger.info("Subscribed {} to MailChimp", email)
              } else {
                logger.warn("MailChimp refused to subscribe {}, with status code {}: {}", email, response.status, response.body)
              }
            }
          }
      }
    }
  }
}
