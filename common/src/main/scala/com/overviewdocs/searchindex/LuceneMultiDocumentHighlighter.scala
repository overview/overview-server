package com.overviewdocs.searchindex

import java.util.Locale
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.{IndexSearcher,Query}
import org.apache.lucene.search.uhighlight._
import scala.collection.mutable.ArrayBuffer

class LuceneMultiDocumentHighlighter(indexSearcher: IndexSearcher, analyzer: Analyzer)
extends UnifiedHighlighter(indexSearcher, analyzer)
{
  private val MaxPassages = 2
  private val MaxPassageSize = 80

  setFormatter(LuceneMultiDocumentHighlighter.passageFormatter)

  override protected def getBreakIterator(field: String) = {
    val original = super.getBreakIterator(field)
    BoundedBreakIteratorScanner.getSentence(Locale.ROOT, MaxPassageSize)
  }

  /** Don't produce summaries when there are no matches. */
  override protected def getMaxNoHighlightPassages(field: String) = {
    // The BoundedBreakIteratorScanner has no next(), and that means attempting
    // to summarize a document with this highlighter would crash.
    0
  }

  /** Returns Snippets marking document matches.
    *
    * If no matching Snippets can be found, returns an empty Array.
    */
  def highlightFieldAsSnippets(field: String, query: Query, docId: Int): Array[Utf16Snippet] = {
    val map = highlightFieldsAsObjects(Array(field), query, Array(docId), Array(MaxPassages))
    val fieldResults: Array[Object] = map.getOrDefault(field, Array.empty)
    val docResults: Array[Utf16Snippet] = fieldResults.headOption.map(_.asInstanceOf[Array[Utf16Snippet]]).getOrElse(Array.empty)
    if (docResults == null) return Array.empty // Lucene breaking my heart
    docResults
      //.filter(_.highlights.nonEmpty)
  }
}

object LuceneMultiDocumentHighlighter {
  val passageFormatter = new PassageFormatter {
    /** Return an Array[Utf16Snippet] */
    override def format(passages: Array[Passage], content: String) = {
      val ret = ArrayBuffer.empty[Utf16Snippet]

      for (passage <- passages) {
        val highlights = ArrayBuffer.empty[Utf16Highlight]
        passage.getMatchStarts.zip(passage.getMatchEnds).take(passage.getNumMatches).map { case ((start: Int, end: Int)) =>
          highlights.lastOption match {
            case Some(highlight) if highlight.end >= start => {
              // According to Lucene's DefaultPassageFormatter.java:
              // "it's possible to have overlapping terms"
              highlights(highlights.length - 1) = Utf16Highlight(highlight.begin, end)
            }
            case _ => highlights.append(Utf16Highlight(start, end))
          }
        }

        ret.append(Utf16Snippet(passage.getStartOffset, passage.getEndOffset, highlights.toVector))
      }

      ret.toArray
    }
  }
}
