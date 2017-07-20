package views

import java.util.Locale
import play.api.i18n.Messages

import controllers.AssetsFinder

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
case class ScopedMessages(scope: String, messages: Messages) {
  /**
   * @return a translated message for the given sub-key
   */
  def apply(key: String, args: Any*) : String = {
    messages(scope + "." + key, args : _*)
  }

  /**
   * @return a translated message for the given sub-key, or None if the
   *         key isn't translated.
   */
  def optional(key: String, args: Any*) : Option[String] = {
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

  def scopedMessages(scope: String)(implicit messages: Messages) = ScopedMessages(scope, messages)

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
  def displayLanguageCode(code: String)(implicit messages: Messages) = {
    val locale = Locale.forLanguageTag(code)
    locale.getDisplayLanguage(messages.lang.toLocale)
  }

  /** Returns a &lt;script&gt; tag for a RequireJS bundle.
    *
    * <tt>requireJsBundle("PublicDocumentSet/index")</tt> will give
    * <tt>&lt;script src="...require.js" data-main=".../PublicDocumentSet/index.js"&gt;&lt;/script&gt;</tt>
    */
  def requireJsBundle(assets: AssetsFinder, module: String) = {
    <script
      src={assets.path("javascripts/vendor/require.js")}
      data-main={assets.path(s"javascripts/bundle/${module}.js")}
      ></script>
  }

  /** Returns a String describing the number of ms before something finishes.
    */
  def shouldFinishInMs(ms: Long)(implicit messages: Messages): String = {
    if (ms < 1000) {
      messages("time_display.shouldFinishIn.zero")
    } else if (ms < 1000 * 60) {
      messages("time_display.shouldFinishIn.seconds", ms / 1000)
    } else if (ms < 1000 * 60 * 60) {
      messages("time_display.shouldFinishIn.minutes", ms / 1000 / 60)
    } else {
      messages("time_display.shouldFinishIn.hours", ms / 1000 / 60 / 60)
    }
  }
}
