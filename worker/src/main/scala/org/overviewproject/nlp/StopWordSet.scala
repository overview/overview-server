package org.overviewproject.nlp

import java.io.InputStream

object StopWordSet {
  private val DefaultStopWordsFile: String = "/stopwords-en.csv"
    
  private def stopWordsFile(lang: String): InputStream = {
	val filename = s"/stopwords-$lang.csv"
	val stopWordStream = getClass.getResourceAsStream(filename)
	
	if (stopWordStream == null) getClass.getResourceAsStream(DefaultStopWordsFile)
	else stopWordStream
  }
    
  def apply(lang: String): Set[String] = {
    val stopWordLines = io.Source.fromInputStream(stopWordsFile(lang)).getLines
  
    stopWordLines.toSet
  }
  
}