package models

import play.api.Play
import play.api.Play.current
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.annotation.adapters.HexBinaryAdapter

/** Values we pass to Intercom.
  *
  * See http://docs.intercom.io/Installing-intercom
  * and http://docs.intercom.io/enable-secure-mode
  *
  * "createdAt" is actually derived from user.confirmedAt.
  */
case class IntercomSettings(email: String, createdAt: Int, userHash: String, appId: String)

trait IntercomConfiguration {
  protected def getConfigString(key: String) : Option[String]

  private lazy val maybeAppId = getConfigString("analytics.intercom.app_id")
  private lazy val maybeKeySpec = {
    for (secret <- getConfigString("analytics.intercom.secret_key") if secret.length > 0)
      yield new SecretKeySpec(secret.getBytes(), "HmacSHA256")
  }

  /** Calculates SHA256 HMAC of the given string with the given key. */
  private def sha256(message: String, keySpec: SecretKeySpec) = {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(keySpec)

    val output = mac.doFinal(message.getBytes())
    (new HexBinaryAdapter).marshal(output)
  }

  /** Gives an IntercomSettings object for the given User.
    *
    * If the IntercomSettings object is None, that's because we cannot use
    * Intercom for this user. (This happens if the user has not been
    * confirmed, for instance. Admin-generated users are like this. A nice
    * side-effect: users we create for integration tests don't use Intercom.)
    */
  def settingsForUser(user: User) : Option[IntercomSettings] = for(
    appId <- maybeAppId if appId.length > 0;
    keySpec <- maybeKeySpec;
    confirmedAt <- user.confirmedAt
  ) yield {
    IntercomSettings(user.email, (confirmedAt.getTime() / 1000).toInt, sha256(user.email, keySpec), appId)
  }
}

/** Gets Intercom configuration.
  *
  * Usage:
  *
  *     val settings = IntercomConfiguration.settingsForUser(user: String)
  *     settings.map(_.email) // user email
  *     settings.map(_.createdAt) // user confirmedAt.getTime() / 1000
  *     settings.map(_.userHash) // SHA256 hash of user email with Intercom key
  *     settings.map(_.appId) // Intercom app ID
  *
  * If there is no config setting for analytics.intercom.app_id or
  * analytics.intercom.secret_key, settings will be None.
  */
object IntercomConfiguration extends IntercomConfiguration {
  override def getConfigString(key: String) = Play.application.configuration.getString(key)
}
