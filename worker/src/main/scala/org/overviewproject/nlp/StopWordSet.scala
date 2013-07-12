package org.overviewproject.nlp

import java.io.InputStream
import java.io.ByteArrayInputStream
import org.overviewproject.util.Logger

object StopWordSet {
  private val DefaultStopWordsFile: String = "/stopwords-en.csv"
  private val EmptyStopWordsStream: InputStream = new ByteArrayInputStream(Array.empty)
  
  def apply(lang: String, suppliedStopWordString: Option[String]): Set[String] = {
    val stopWordLines = io.Source.fromInputStream(stopWordsFile(lang)).getLines
    val suppliedStopWords = extractStopWords(suppliedStopWordString)
    
    stopWordLines.toSet ++ suppliedStopWords
  }

  private def stopWordsFile(lang: String): InputStream = {
    val filename = s"/stopwords-$lang.csv"
    val possibleStopWordStreams: Seq[InputStream] = Seq(filename, DefaultStopWordsFile).map(fileAsStream) 

    possibleStopWordStreams.find(_ != null).getOrElse {
      Logger.error("No stopwords file found")
      EmptyStopWordsStream
    }
  }
  
  private def fileAsStream(filename: String): InputStream = getClass.getResourceAsStream(filename)

  private def extractStopWords(wordString: Option[String]): Set[String] = wordString.map { s =>
    val whitespace = """[\s\u00A0]+""".r
    whitespace.split(s.trim).toSet
  }.getOrElse(Set.empty[String]) 
    
}