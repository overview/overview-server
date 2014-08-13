package models

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class IntercomConfigurationSpec extends Specification {
  trait BaseScope extends Scope {
    val secretKey : Option[String] = Some("secret")
    val appId : Option[String] = Some("id")
    val user = User(email="user@example.org", confirmedAt=Some(new java.sql.Timestamp(12345678)))

    def config = new IntercomConfiguration() {
      override protected def getConfigString(key: String) = key match {
        case "analytics.intercom.secret_key" => secretKey
        case "analytics.intercom.app_id" => appId
        case _ => None
      }
    }

    lazy val settings = config.settingsForUser(user)
  }

  "IntercomConfiguration" should {
    "return no settings when there is no app ID" in new BaseScope {
      override val appId = None
      settings must beNone
    }

    "return no settings when app ID is empty string" in new BaseScope {
      override val appId = Some("")
      settings must beNone
    }

    "return no settings when there is no secret key" in new BaseScope {
      override val secretKey = None
      settings must beNone
    }

    "return no settings when the secret key is the empty string" in new BaseScope {
      override val secretKey = Some("")
      settings must beNone
    }

    "return no settings when there is no confirmedAt" in new BaseScope {
      override val user = User(email="user@example.org", confirmedAt=None)
      settings must beNone
    }

    "settings" should {
      "have email" in new BaseScope {
        settings.map(_.email) must beSome("user@example.org")
      }

      "have createdAt" in new BaseScope {
        settings.map(_.createdAt) must beSome(12345)
      }

      "have appId" in new BaseScope {
        settings.map(_.appId) must beSome("id")
      }

      "have userHash" in new BaseScope {
        // generated test data at http://www.freeformatter.com/hmac-generator.html
        settings.map(_.userHash) must beSome("8D2D43A0E50D815C632BC9ED6EF4170DD4E1757CF14915426419EF0BC280CED7")
      }
    }
  }
}
