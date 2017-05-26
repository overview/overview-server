package com.overviewdocs.nlp

import scala.io

import com.overviewdocs.util.Logger

object StopWordSet {
  def apply(lang: String, suppliedStopWords: Seq[String]): Set[String] = {
    langStopWords(lang) ++ suppliedStopWords.map(_.toLowerCase)
  }

  private def langStopWords(lang: String): Set[String] = {
    val inputStream = getClass.getResourceAsStream(s"/stopwords-$lang.csv")
    io.Source.fromInputStream(inputStream)(io.Codec.UTF8)
      .getLines
      .map(_.trim)
      .filter(_.nonEmpty)
      .toSet
  }
}
