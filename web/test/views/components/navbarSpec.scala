package views.html.Session

import play.api.Configuration

import com.overviewdocs.models.DocumentSet
import models.{IntercomConfiguration,IntercomSettings,User}
import views.html.components.navbar

class navbar_Spec extends views.ViewSpecification {
  trait OurContext extends HtmlViewSpecificationScope {
    val assets = mock[controllers.AssetsFinder]
    assets.path(anyString) returns "/asset-path"

    def configBranding = Map(
      "logo_h40px_url" -> "",
      "logo_h40px_2x_url" -> "",
      "logo_h40px_width_px" -> "",
    )
    def configContactUrl: String = "https://contact.us"
    def configMultiUser: Boolean = true
    def configOverview = Map(
      "contact_url" -> configContactUrl,
      "multi_user" -> configMultiUser,
    )
    def config = Map(
      "branding" -> configBranding,
      "overview" -> configOverview,
    )
    def configuration: Configuration = Configuration.from(config)
    def maybeIntercomSettings: Option[IntercomSettings] = None
    def intercomConfiguration = new IntercomConfiguration {
      override def settingsForUser(user: User) = maybeIntercomSettings
    }
    def optionalUser: Option[User] = None
    def optionalDocumentSet: Option[DocumentSet] = None

    lazy val view = new navbar(assets, configuration, intercomConfiguration)
    override def result = view(optionalUser, optionalDocumentSet)
  }

  "navbar" should {
    "logo" should {
      "show internal asset when external branding is not configured" in new OurContext {
        override def configBranding = Map(
          "logo_h40px_url" -> "",
          "logo_h40px_2x_url" -> "",
          "logo_h40px_width_px" -> "",
        )
        $("a.logo img").attr("src") must beEqualTo("/asset-path")
        $("a.logo img").attr("srcset") must beEqualTo("/asset-path 1x, /asset-path 2x")
        $("a.logo img").attr("width") must beEqualTo("")
      }

      "show external branding when 1x and 2x are configured" in new OurContext {
        override def configBranding = Map(
          "logo_h40px_url" -> "https://example.com/logo-1x.png",
          "logo_h40px_2x_url" -> "https://example.com/logo-2x.png",
          "logo_h40px_width_px" -> "123",
        )
        $("a.logo img").attr("src") must beEqualTo("https://example.com/logo-1x.png")
        $("a.logo img").attr("srcset") must beEqualTo("https://example.com/logo-1x.png 1x, https://example.com/logo-2x.png 2x")
        $("a.logo img").attr("width") must beEqualTo("123")
      }
    }

    "contact link" should {
      "show Intercom when configured and logged in" in new OurContext {
        override def optionalUser = Some(User(email="user@example.com"))
        override def maybeIntercomSettings = Some(IntercomSettings("user@example.com", 123, "user-hash", "app-id"))
        $("li.contact-link a#Intercom").attr("href") must beEqualTo("mailto:app-id@incoming.intercom.io")
      }

      "hide Intercom when configured and logged out" in new OurContext {
        override def optionalUser = None
        override def maybeIntercomSettings = Some(IntercomSettings("user@example.com", 123, "user-hash", "app-id"))
        $("li.contact-link a#Intercom").length must beEqualTo(0)
      }

      "show overview.contact_url when configured" in new OurContext {
        override def configContactUrl = "https://contact.us"
        $("li.contact-link a").attr("href") must beEqualTo("https://contact.us")
      }
    }
  }
}
