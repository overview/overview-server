package com.overviewdocs.searchindex

import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.search.{IndexSearcher,Query}
import org.apache.lucene.search.uhighlight._
import scala.collection.mutable.ArrayBuffer

class LuceneSingleDocumentHighlighter(indexSearcher: IndexSearcher, analyzer: Analyzer)
extends UnifiedHighlighter(indexSearcher, analyzer)
{
  private val MaxPassages = 1000

  setFormatter(LuceneSingleDocumentHighlighter.passageFormatter)

  def highlightFieldAsHighlights(field: String, query: Query, docId: Int): Array[Utf16Highlight] = {
    val map = highlightFieldsAsObjects(Array(field), query, Array(docId), Array(MaxPassages))
    val fieldResults: Array[Object] = map.get(field)
    val docResults: Array[Utf16Highlight] = fieldResults.headOption.map(_.asInstanceOf[Array[Utf16Highlight]]).getOrElse(Array.empty)
    if (docResults == null) return Array.empty // Lucene breaking my heart
    docResults
  }
}

object LuceneSingleDocumentHighlighter {
  val passageFormatter = new PassageFormatter {
    /** Return an Array[Utf16Highlight] */
    override def format(passages: Array[Passage], content: String) = {
      val highlights = ArrayBuffer.empty[Utf16Highlight]

      for (passage <- passages) {
        passage.getMatchStarts.zip(passage.getMatchEnds).take(passage.getNumMatches).map { case ((start: Int, end: Int)) =>
          highlights.append(Utf16Highlight(start, end))
        }
      }

      val sorted = highlights.sorted(math.Ordering.by({ x: Utf16Highlight => (x.begin, x.end) }))

      // According to Lucene's DefaultPassageFormatter.java:
      // "it's possible to have overlapping terms". Let's remove duplicates.
      mergeOverlappingHighlights(sorted).toArray
    }

    def mergeOverlappingHighlights(highlights: Seq[Utf16Highlight]): ArrayBuffer[Utf16Highlight] = {
      val ret = new ArrayBuffer[Utf16Highlight](highlights.length)

      for (highlight <- highlights) {
        ret.lastOption match {
          case Some(lastHighlight) if lastHighlight.end >= highlight.begin => {
            // Replace previous highlight, so it merges with this one
            ret(ret.length - 1) = Utf16Highlight(lastHighlight.begin, Seq(highlight.end, lastHighlight.end).max)
          }
          case _ => ret.append(highlight)
        }
      }

      ret
    }
  }
}
