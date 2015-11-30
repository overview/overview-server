/*
 * WeightedLexer.scala
 * Tokenizer that specifically recognizes and up-wieghts certain terms
 * Basic algorithm is same as Lexer: nreak on spaces, remove most punctuation, lowercase.
 * But we also match against supplied list of regexes. If any found:
 *  - don't lowercase or remove punct
 *  - output user-defined weight
 * Regexs matches override stopwords. If more than one regex matched, multiply weights.
 *
 * Overview
 *
 * Created by Jonathan Stray, October 2013
 *
 */
package com.overviewdocs.nlp

import java.util.regex.Pattern
import scala.util.control.Exception

import com.overviewdocs.nlp.DocumentVectorTypes.TermWeight

case class WeightedTermString(term: String, weight: TermWeight)
case class WeightedTermRegex(pattern: Pattern, weight: TermWeight)

class WeightedLexer(val stopWords: Set[String], val weightedTerms: Map[String,TermWeight]) {
  // None iff there's an invalid regex (TODO make the *caller* compile regexes)
  private val weightedTermRegexes: Option[Seq[WeightedTermRegex]] = {
    Exception.catching(classOf[java.util.regex.PatternSyntaxException]).opt {
      weightedTerms
        .toSeq // (String, TermWeight)
        .map({ case (s, w) => WeightedTermRegex(Pattern.compile(s), w) })
    }
  }

  // longest token we'll tolerate (silently truncates, to prevent bad input from destroying everything downstream)
  private[nlp] val maxTokenLength = 40

  // Remove certain types of terms. At the moment:
  //  - we insist that terms are at least three chars
  //  - discard if a) starts with a digit and b) 40% or more of the characters are digits
  //  - discard stop words
  private def termAcceptable(t: String): Boolean = {
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
  private def processTerm(unboundedTerm: String) : Option[WeightedTermString] ={
    // Copy the string with new, to avoid chewing up memory. (String.split keeps ref to original bytes)
    // Also, truncate it.
    val term = new String(unboundedTerm.take(maxTokenLength))

    var weight: TermWeight = 1
    var matched: Boolean = false

    for (regex <- weightedTermRegexes.get if regex.pattern.matcher(term).find) {
      weight *= regex.weight
      matched = true
    }

    if (matched) {
      // Got a match, return unprocessed term (case sensitive etc.)
      Some(WeightedTermString(term, weight))
    } else {
      val lowerTerm = term.toLowerCase
      if (termAcceptable(lowerTerm)) {
        Some(WeightedTermString(lowerTerm, 1))
      } else {
        None
      }
    }
  }

  /** Given some text, builds a list of weighted terms. */
  def makeTerms(textIn: String): Seq[WeightedTermString] = {
    if (!weightedTermRegexes.isDefined) throw new Exception("bad_important_words_pattern")

    // Assume the input is a bunch of tokens
    textIn.split(' ').flatMap(processTerm)
  }
}
