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

  def highlightFieldAsHighlights(field: String, query: Query, docId: Int): Array[Highlight] = {
    val map = highlightFieldsAsObjects(Array(field), query, Array(docId), Array(MaxPassages))
    val results: Array[Object] = map.get(field)
    results.flatMap(_.asInstanceOf[Array[Highlight]])
  }
}

object LuceneSingleDocumentHighlighter {
  val passageFormatter = new PassageFormatter {
    /** Return an Array[Highlight] */
    override def format(passages: Array[Passage], content: String) = {
      val highlights = ArrayBuffer.empty[Highlight]

      for (passage <- passages) {
        passage.getMatchStarts.zip(passage.getMatchEnds).map { case ((start: Int, end: Int)) =>
          if (start != end) {
            highlights.append(Highlight(start, end))
          }
        }
      }

      val sorted = highlights.sorted(math.Ordering.by({ x: Highlight => (x.begin, x.end) }))

      // According to Lucene's DefaultPassageFormatter.java:
      // "it's possible to have overlapping terms". Let's remove duplicates.
      mergeOverlappingHighlights(sorted).toArray
    }

    def mergeOverlappingHighlights(highlights: Seq[Highlight]): ArrayBuffer[Highlight] = {
      val ret = new ArrayBuffer[Highlight](highlights.length)

      for (highlight <- highlights) {
        ret.lastOption match {
          case Some(lastHighlight) if lastHighlight.end >= highlight.begin => {
            // Replace previous highlight, so it merges with this one
            ret(ret.length - 1) = Highlight(lastHighlight.begin, Seq(highlight.end, lastHighlight.end).max)
          }
          case _ => ret.append(highlight)
        }
      }

      ret
    }
  }
}
