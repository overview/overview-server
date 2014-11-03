package views

import java.util.Locale
import play.api.i18n.{Lang,Messages}
import play.api.Play
import play.api.Play.current

/**
 * A convenience class on top of Messages.
 *
 * Usage:
 *
 *     val m = ScopedMessages("toplevel.key")
 *     val message = m("subkey")
 *
 * This is equivalent to:
 *
 *     val message = Messages("toplevel.key.subkey")
 */
case class ScopedMessages(scope: String) {
  /**
   * @return a translated message for the given sub-key
   */
  def apply(key: String, args: Any*)(implicit lang: Lang) : String = {
    Messages(scope + "." + key, args : _*)
  }

  /**
   * @return a translated message for the given sub-key, or None if the
   *         key isn't translated.
   */
  def optional(key: String, args: Any*)(implicit lang: Lang) : Option[String] = {
    val ret = apply(key, args)
    if (ret == scope + "." + key) {
      None
    } else {
      Some(ret)
    }
  }
}

/*
 * Functions that every template can access.
 */
object Magic {
  val t = play.api.i18n.Messages
  val scopedMessages = ScopedMessages

  private val InvalidCharRegex = """[\x00-\x1f\x7f"*/:<>\?\\|#]""".r

  /** Returns a String that can be downloaded to the filesystem.
   *
   * The returned filename has two useful properties:
   *
   * * It can be included in URLs ("/", "?" and "#" are replaced).
   * * It can be saved to any filesystem (and VFAT in particular). See
   *   http://en.wikipedia.org/wiki/Filename#Reserved_characters_and_words
   *
   * From a certain perspective, the returned filename can be a basename
   * anywhere: even on VFAT and HTTP.
   *
   * This escape is lossy.
   */
  def escapeFilename(filename: String) = {
    InvalidCharRegex.replaceAllIn(filename, "_")
  }

  /** Returns "French" when code is "fr", for instance. */
  def displayLanguageCode(code: String)(implicit lang: Lang) = {
    val locale = Locale.forLanguageTag(code)
    locale.getDisplayLanguage(lang.toLocale)
  }

  /** Returns a &lt;script&gt; tag for a RequireJS bundle.
    *
    * <tt>requireJsBundle("PublicDocumentSet/index")</tt> will give
    * <tt>&lt;script src="...require.js" data-main=".../PublicDocumentSet/index.js"&gt;&lt;/script&gt;</tt>
    */
  def requireJsBundle(module: String) = {
    <script
      src={controllers.routes.Assets.at("javascripts/require.js").url}
      data-main={controllers.routes.Assets.at(s"javascripts/bundle/${module}.js").url}
      ></script>
  }
}
