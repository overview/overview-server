package models

import com.google.inject.ImplementedBy
import play.api.Play
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import java.nio.charset.StandardCharsets
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

/** Values we pass to Intercom.
  *
  * See http://docs.intercom.io/Installing-intercom
  * and http://docs.intercom.io/enable-secure-mode
  *
  * "createdAt" is actually derived from user.confirmedAt.
  */
case class IntercomSettings(email: String, createdAt: Int, userHash: String, appId: String)

@ImplementedBy(classOf[DefaultIntercomConfiguration])
trait IntercomConfiguration {
  /** Gives an IntercomSettings object for the given User.
    *
    * If the IntercomSettings object is None, that's because we cannot use
    * Intercom for this user. (This happens if the user has not been
    * confirmed, for instance. Admin-generated users are like this. A nice
    * outcome: users we create for integration tests don't use Intercom.)
    */
  def settingsForUser(user: User) : Option[IntercomSettings]
}

class DefaultIntercomConfiguration @Inject() (
  configuration: play.api.Configuration
) extends IntercomConfiguration {
  private val appId = configuration.get[String]("analytics.intercom.app_id")
  private val secretKey = configuration.get[String]("analytics.intercom.secret_key")
  private lazy val keySpec: SecretKeySpec = {
    val keyBytes = secretKey.getBytes(StandardCharsets.UTF_8)
    new SecretKeySpec(keyBytes, "HmacSHA256")
  }

  /** Calculates SHA256 HMAC of the given string with the given key. */
  private def sha256(message: String) = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(keySpec)

    val output = mac.doFinal(message.getBytes(StandardCharsets.UTF_8))
    (new HexBinaryAdapter).marshal(output)
  }

  def settingsForUser(user: User) : Option[IntercomSettings] = {
    if (appId.nonEmpty && secretKey.nonEmpty) {
      for(
        confirmedAt <- user.confirmedAt
      ) yield {
        IntercomSettings(user.email, (confirmedAt.getTime() / 1000).toInt, sha256(user.email), appId)
      }
    } else {
      None
    }
  }
}
