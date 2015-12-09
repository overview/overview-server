package com.overviewdocs.models

import com.ibm.icu.lang.{UCharacter,UScript}
import com.ibm.icu.lang.UCharacterEnums.ECharacterCategory
import com.ibm.icu.text.{BreakIterator,RuleBasedBreakIterator,UTF16}
import com.ibm.icu.util.ULocale
import java.io.StringReader
import java.util.Date
import java.text.{Normalizer,StringCharacterIterator}
import play.api.libs.json.JsObject
import scala.collection.mutable

/** A complete Document.
  *
  * The `text` field can make this rather large. If you don't need the `text`
  * field, use a DocumentInfo object instead.
  */
case class Document(
  override val id: Long,
  override val documentSetId: Long,
  override val url: Option[String],
  override val suppliedId: String,
  override val title: String,
  override val pageNumber: Option[Int],
  override val keywords: Seq[String],
  override val createdAt: Date,
  val fileId: Option[Long],
  val pageId: Option[Long],
  override val displayMethod: DocumentDisplayMethod.Value,
  override val isFromOcr: Boolean,
  override val metadataJson: JsObject,
  override val text: String
) extends DocumentHeader {
  def toDocumentInfo: DocumentInfo = DocumentInfo(
    id,
    documentSetId,
    url,
    suppliedId,
    title,
    pageNumber,
    keywords,
    createdAt,
    displayMethod,
    isFromOcr,
    fileId.isDefined
  )

  override def viewUrl: Option[String] = {
    url
      .orElse(fileId.map(_ => s"/documents/${id}.pdf"))
  }

  /** Text, normalized as NFKC. */
  def normalizedText: String = Normalizer.normalize(text, Normalizer.Form.NFKC)

  /** Returns NFKC-normalized tokens split according to Unicode tr29. */
  override def tokens: Seq[String] = {
    val ret = mutable.ArrayBuffer[String]()
    val nt: String = normalizedText
    val bi = new Document.CompositeBreakIterator()
    bi.setText(nt)

    while (true) {
      val current = bi.current
      val next = bi.next

      if (next == BreakIterator.DONE) {
        return ret
      }

      if (bi.getRuleStatus != BreakIterator.WORD_NONE) {
        ret.+=(nt.substring(current, next))
      }
    }

    ???
  }
}

object Document {
  // Lots of this is copied from org.apache.lucene.analysis.icu.segmentation.
  // We don't depend on it because it's inextensible. (We'd rather not include
  // all of Lucene.)
  private val CjkFactory = new WordBreakIteratorFactory(ULocale.ROOT)
  private val DefaultFactory = new RuleBasedBreakIteratorFactory("Default.brk")
  private val KhmerFactory = new RuleBasedBreakIteratorFactory("Khmer.brk")

  private trait OurBreakIteratorFactory {
    def build: OurBreakIterator
  }

  /** Returns the current codepoint, and move the iterator to the next one.
    */
  private def currentCodepointAndIncrement(charIterator: StringCharacterIterator): Int = {
    val c1 = charIterator.current
    val ret = _finishCodepoint(c1, charIterator)
    charIterator.next
    ret
  }

  private def _finishCodepoint(c1: Char, charIterator: StringCharacterIterator): Int = {
    if (Character.isHighSurrogate(c1)) {
      Character.toCodePoint(c1, charIterator.next)
    } else {
      c1.toInt
    }
  }

  private trait OurBreakIterator {
    // Copied from Lucene's BreakIteratorWrapper
    def first: Int
    def next: Int
    def current: Int
    def getRuleStatus: Int
    def setText(it: StringCharacterIterator): Unit
  }

  private class CompositeBreakIterator {
    private val breakIterators = new Array[OurBreakIterator](UScript.CODE_LIMIT)
    private var breakIterator: OurBreakIterator = getBreakIterator(UScript.COMMON)
    private val scriptIterator = new ScriptIterator
    private var text: String = ""

    /** Retrieve the next break position.
      *
      * If the BreakIterator range is exhausted within the script boundary,
      * examine the next script boundary.
      *
      * @return The next break position or -1 (BreakIterator.DONE).
      */
    def next: Int = {
      var ret = breakIterator.next

      while (ret == BreakIterator.DONE && scriptIterator.next) {
        breakIterator = getBreakIterator(scriptIterator.scriptCode)
        breakIterator.setText(new StringCharacterIterator(
          text,
          scriptIterator.scriptStart,
          scriptIterator.scriptLimit,
          scriptIterator.scriptStart
        ))
        ret = breakIterator.next
      }

      ret
    }

    def current: Int = breakIterator.current
    def getRuleStatus: Int = breakIterator.getRuleStatus

    def setText(s: String): Unit = {
      text = s
      scriptIterator.setText(new StringCharacterIterator(text))
      if (scriptIterator.next) {
        breakIterator = getBreakIterator(scriptIterator.scriptCode)
        breakIterator.setText(new StringCharacterIterator(
          text,
          scriptIterator.scriptStart,
          scriptIterator.scriptLimit,
          scriptIterator.scriptStart
        ))
      } else {
        breakIterator = getBreakIterator(UScript.COMMON)
        breakIterator.setText(new StringCharacterIterator(""))
      }
    }

    private def buildBreakIterator(scriptCode: Int): OurBreakIterator = scriptCode match {
      case UScript.KHMER => KhmerFactory.build
      case UScript.JAPANESE => CjkFactory.build
      case _ => DefaultFactory.build
    }

    private def getBreakIterator(scriptCode: Int): OurBreakIterator = {
      if (breakIterators(scriptCode) == null) {
        breakIterators(scriptCode) = buildBreakIterator(scriptCode)
      }
      breakIterators(scriptCode)
    }
  }

  private class WordBreakIteratorFactory(locale: ULocale) extends OurBreakIteratorFactory {
    private val bi = BreakIterator.getWordInstance(locale)
    override def build = new BIWrapper(bi.clone.asInstanceOf[BreakIterator])
  }

  private class RuleBasedBreakIteratorFactory(resource: String) extends OurBreakIteratorFactory {
    private val is = getClass.getResourceAsStream(s"/lucene/$resource")
    private val bi = RuleBasedBreakIterator.getInstanceFromCompiledRules(is)
    is.close

    override def build = new RBBIWrapper(bi.clone.asInstanceOf[RuleBasedBreakIterator])
  }

  private class RBBIWrapper(bi: BreakIterator) extends OurBreakIterator {
    override def first = bi.first
    override def next = bi.next
    override def current = bi.current
    override def getRuleStatus = bi.getRuleStatus
    override def setText(it: StringCharacterIterator) = bi.setText(it)
  }

  private class BIWrapper(bi: BreakIterator) extends OurBreakIterator {
    private var status: Int = 0
    private var textIt: StringCharacterIterator = new StringCharacterIterator("")

    override def getRuleStatus = status
    override def current = bi.current
    override def first = bi.first

    override def setText(it: StringCharacterIterator) = {
      textIt = it.clone.asInstanceOf[StringCharacterIterator]
      bi.setText(it)
    }

    override def next = {
      val current = bi.current
      val next = bi.next
      status = calcStatus(current, next)
      next
    }

    private def calcStatus(current: Int, next: Int): Int = {
      if (current == BreakIterator.DONE || next == BreakIterator.DONE) {
        return BreakIterator.WORD_NONE;
      }

      while (textIt.getIndex < next) {
        val codepoint = currentCodepointAndIncrement(textIt)
        if (UCharacter.isDigit(codepoint)) {
          return BreakIterator.WORD_NUMBER
        } else if (UCharacter.isLetter(codepoint)) {
          return BreakIterator.WORD_LETTER
        }
      }

      BreakIterator.WORD_NONE
    }
  }

  private class ScriptIterator() {
    // Copied from Lucene's ScriptIterator
    private var textIt: StringCharacterIterator = new StringCharacterIterator("")
    var scriptStart: Int = 0
    var scriptLimit: Int = 0
    var scriptCode: Int = 0

    // linear fast-path for basic latin case
    private val BasicLatin: Array[Int] = Array.tabulate(128)(UScript.getScript _)

    def next: Boolean = {
      if (textIt.getIndex == textIt.getEndIndex) return false

      scriptCode = UScript.COMMON
      scriptStart = scriptLimit

      while (textIt.getIndex < textIt.getEndIndex) {
        val ch = currentCodepointAndIncrement(textIt)
        val sc = getScript(ch)

        /*
         * From UTR #24: Implementations that determine the boundaries between
         * characters of given scripts should never break between a non-spacing
         * mark and its base character. Thus for boundary determinations and
         * similar sorts of processing, a non-spacing mark — whatever its script
         * value — should inherit the script value of its base character.
         */
        if (isSameScript(scriptCode, sc) || UCharacter.getType(ch) == ECharacterCategory.NON_SPACING_MARK) {
          // Inherited or Common becomes the script code of the surrounding text.
          if (scriptCode <= UScript.INHERITED && sc > UScript.INHERITED) {
            scriptCode = sc
          }
        } else {
          scriptLimit = textIt.getIndex - UTF16.getCharCount(ch)
          return true
        }
      }

      scriptLimit = textIt.getIndex
      return true
    }

    def setText(it: StringCharacterIterator): Unit = {
      textIt = it
    }

    private def isSameScript(scriptOne: Int, scriptTwo: Int): Boolean = {
      scriptOne <= UScript.INHERITED || scriptTwo <= UScript.INHERITED || scriptOne == scriptTwo
    }

    // fast version of UScript.getScript(). Basic Latin is an array lookup
    private def getScript(codepoint: Int): Int = {
      if (0 <= codepoint && codepoint <= BasicLatin.length) {
        BasicLatin(codepoint)
      } else {
        val script: Int = UScript.getScript(codepoint)
        if (script == UScript.HAN || script == UScript.HIRAGANA || script == UScript.KATAKANA) {
          UScript.JAPANESE
        } else if (0xFF10 <= codepoint && codepoint <= 0xFF19) {
          // when using CJK dictionary breaking, don't let full width numbers go to it, otherwise
          // they are treated as punctuation. we currently have no cleaner way to fix this!
          UScript.LATIN
        } else {
          script
        }
      }
    }
  }
}
