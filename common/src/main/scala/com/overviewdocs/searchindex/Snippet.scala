package com.overviewdocs.searchindex

import scala.collection.immutable.Vector
import scala.collection.mutable.ArrayBuffer

sealed trait Snippet {
  /** Returns tokens describing how to format this Snippet.
    *
    * @param String text Text the snippet points to.
    */
  def tokenize(text: String): Seq[Snippet.Token]
}

/** A place in a document where a search query was found, plus context.
  *
  * Each snippet points to characters [start,end) in UTF-16-encoded text.
  *
  * Vector is chosen because it's Serializable. TODO use protobuf for Akka
  * serialization.
  */
case class Utf16Snippet(begin: Int, end: Int, highlights: Vector[Utf16Highlight]) extends Snippet {
  override def tokenize(text: String) = {
    val ret = new ArrayBuffer[Snippet.Token](2 + 2 * highlights.length)

    if (begin > 0) {
      ret.append(Snippet.ElisionToken)
    }

    var pos = begin
    for (highlight <- highlights) {
      if (highlight.begin > pos) {
        ret.append(Snippet.TextToken(text.substring(pos, highlight.begin)))
      }

      if (highlight.end > highlight.begin) {
        ret.append(Snippet.HighlightToken(text.substring(highlight.begin, highlight.end)))
      }

      pos = highlight.end
    }

    if (pos < end) {
      ret.append(Snippet.TextToken(text.substring(pos, end)))
    }

    if (end < text.length) {
      ret.append(Snippet.ElisionToken)
    }

    ret
  }
}

object Snippet {
  /** A unit of highlight-able content. */
  sealed trait Token

  /** Un-highlighted text (for context). */
  case class TextToken(text: String) extends Token

  /** Highlighted text. */
  case class HighlightToken(text: String) extends Token

  /** A marker that there is more text we aren't showing. */
  case object ElisionToken extends Token

  /** Merges Token collections and omits duplicate ElisionTokens. */
  def concatTokenCollections(streams: Seq[Seq[Token]]): Seq[Token] = {
    val flattened = streams.flatten.toIndexedSeq

    streams.flatten.toIndexedSeq
    
    flattened.zipWithIndex
      .flatMap(_ match {
        case (Snippet.ElisionToken, index) if index > 0 && flattened(index - 1) == ElisionToken => None
        case (token, _) => Some(token)
      })
  }
}
