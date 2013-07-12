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
package org.overviewproject.nlp

import scala.Array.canBuildFrom

object Lexer {

  // longest token we'll tolerate (silently truncates, to prevent bad input from destroying everything downstream)
  val maxTokenLength = 40

  // Remove certain types of terms. At the moment:
  //  - we insist that terms are at least three chars
  //  - discard if a) starts with a digit and b) 40% or more of the characters are digits
  //  - discard stop words

  def termAcceptable(t: String, stopWords: Set[String]): Boolean = {
    if (t.length < 3)
      false // too short, unacceptable
    else if (stopWords.contains(t))
      false // stop word, unacceptable
    else if (!t.charAt(0).isDigit)
      true // doesn't start with digit, acceptable
    else
      10 * t.count(_.isDigit) < 4 * t.length // < 40% digits, acceptable
  }

  // Given a string and a list of stop words, returns a list of terms. Basically just splits on space, but also cleans up lots
  def makeTerms(textIn: String, stopWords: Set[String]): Seq[String] = {
    if (textIn.length == 0) Seq()

    // lose case, strip HTML/XML tags,
    var text = textIn.toLowerCase
    text = "<[^>]*>".r.replaceAllIn(text, "")

    // collapse runs of spaces (including non-breaking space, U+160) into single space
    text = "[ \t\n\r\u00A0]+".r.replaceAllIn(text, " ")

    // allow only alphanum, dash, apostrophe, space
    text = text.filter(c => c.isDigit || c.isLetter || " -'".contains(c))

    // split on space, keep only "acceptable" terms, truncate to max length
    // And then create a new string for each term, because split() refers back to original text, and we want it to be garbage collected
    // (10/19/2012 a problem in practice -- tokens in the document vector string table were keeping all original doc text in memory!)
    text.split(' ').filter(termAcceptable(_, stopWords)).map(_.take(maxTokenLength)).map(new String(_))
  }

}

