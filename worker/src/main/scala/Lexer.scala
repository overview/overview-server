/*
 * Lexer.scala
 * Simple English language tokenization
 * Break on spaces, remove most punctuation, other characters. Filter stopwords. Optionally parse bigrams from a provided set.
 * 
 * Overview Project
 * 
 * Created by Jonathan Stray, June 2012
 * 
 */
package clustering

import scala.util.matching.Regex
import scala.io.Source

object Lexer {

  
  // Load stopwords on startup
  var stopWords = Set[String]()
  val stopWordsFileName = "conf/stopwords.csv"
  for (l <- io.Source.fromFile(stopWordsFileName).getLines)
    stopWords += l
  
  // Remove certain types of terms. At the moment:
  //  - we insist that terms are at least three chars
  //  - discard if a) starts with a digit and b) 40% or more of the characters are digits
  //  - discard stopwords
  
  def term_acceptable(t:String) : Boolean = {  
    if (t.length < 3)
      false                                    // too short, unacceptable
    else if (stopWords.contains(t))
      false                                    // stopword, unacceptable
    else
      if (!t.charAt(0).isDigit)
        true                                   // doesn't start with digit, acceptable
      else
        10*t.count(_.isDigit) < 4*t.length   // < 40% digits, acceptable
  }
  
  // Given a string, returns a list of terms. Basically just splits on space, but also cleans up lots
  def make_terms(textIn:String) : Array[String] = {
    if (textIn.length == 0) Array()
   
    // lose case, strip HTML/XML tags, 
    var text = textIn.toLowerCase                   
    text = "<[^>]*>".r.replaceAllIn(text,"")
    
    // collapse runs of spaces (including non-breaking space, U+160) into single space
    text = "[ \t\n\r\u0160]+".r.replaceAllIn(text," ")
    
    // allow only alphanum, dash, apostrophe, space
    text = text.filter(c => c.isDigit || c.isLetter || " -'".contains(c))
    
    // split on space, keep only "acceptable" terms
    text.split(' ').filter(term_acceptable)
  }
  
}
  
