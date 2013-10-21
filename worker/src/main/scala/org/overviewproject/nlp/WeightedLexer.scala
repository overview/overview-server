/*
 * WeightedLexer.scala
 * Tokenizer that specifically recognizes and up-wieghts certain terms
 * Basic algorithm is same as Lexer: nreak on spaces, remove most punctuation, lowercase.
 * But we also match against supplied list of regexes. If any found:
 *  - don't lowercase or remove punct
 *  - output user-defined weight 
 * Regexs matches override stopwords. If more than one regex matched, multiply weights. 
 *
 * Overview Project
 *
 * Created by Jonathan Stray, October 2013
 *
 */
package org.overviewproject.nlp
import org.overviewproject.nlp.DocumentVectorTypes.TermWeight

case class WeightedTermString(term:String,weight:TermWeight)

class WeightedLexer(val stopWords:Set[String], val weightedTerms:Map[String,TermWeight]) {

  // longest token we'll tolerate (silently truncates, to prevent bad input from destroying everything downstream)
  val maxTokenLength = 40

  // Remove certain types of terms. At the moment:
  //  - we insist that terms are at least three chars
  //  - discard if a) starts with a digit and b) 40% or more of the characters are digits
  //  - discard stop words

  def termAcceptable(t: String): Boolean = {
    if (t.length < 3)
      false // too short, unacceptable
    else if (stopWords.contains(t))
      false // stop word, unacceptable
    else if (!t.charAt(0).isDigit)
      true // doesn't start with digit, acceptable
    else
      10 * t.count(_.isDigit) < 4 * t.length // < 40% digits, acceptable
  }

  // If the raw term string matches any of the regex patterns, we emit the term and the pattern weight
  // multiply the weights of the matched patterns if more than one
  // Otherwise standard processing strips punctuation, lowecases, checks termAcceptable
  // Throws out invalid terms.
  def ProcessTerm(rawTerm:String) : Option[WeightedTermString] ={

    var weight:TermWeight = 1
    var matched = false

    weightedTerms foreach { case (pattern,patternWeight) => 
      if (rawTerm.matches(pattern)) {
        weight *= patternWeight
        matched = true 
      }
    }

    if (matched) {
      // Got a match, return unprocessed term (case sensitive etc.)
      Some(WeightedTermString(rawTerm, weight))
    } else {
      // lose case, allow only alphanum, dash, apostrophe
      var term = rawTerm.toLowerCase.filter(c => c.isDigit || c.isLetter || "-'".contains(c))
      if (termAcceptable(term))
        Some(WeightedTermString(term, 1f))
      else
        None
    }
  }

  // Clips term to maximum length (avoids pathalogical cases)
  // also copies it with new (avoids substring references created by .split chewing up all our memory)
  def limitTermLength(wt:WeightedTermString) = 
    WeightedTermString(new String(wt.term.take(maxTokenLength)), wt.weight)

  // Given a string and a list of stop words, returns a list of weighted terms. 
  def makeTerms(textIn: String): Seq[WeightedTermString] = {
    if (textIn.length == 0) Seq()

    // collapse runs of spaces (including non-breaking space, U+160) into single space
    val text = "[ \t\n\r\u00A0]+".r.replaceAllIn(textIn, " ")

    // split on (condensed) spaces and weight each term
    text.split(' ').flatMap(ProcessTerm).map(limitTermLength)
  }
}

