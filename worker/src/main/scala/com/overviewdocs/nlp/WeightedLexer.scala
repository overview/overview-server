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

import java.io.StringReader
import java.text.Normalizer
import java.util.regex.Pattern
import org.apache.lucene.analysis.standard.StandardTokenizer // Built in to ElasticSearch
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute // Built in to ElasticSearch
import scala.collection.mutable.ArrayBuffer
import scala.util.control.Exception

import com.overviewdocs.nlp.DocumentVectorTypes.TermWeight
import com.overviewdocs.util.DisplayedError

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
  private def processTerm(term: String) : Option[WeightedTermString] ={
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

  // Clips term to maximum length (avoids pathalogical cases)
  // also copies it with new (avoids substring references created by .split chewing up all our memory)
  private def limitTermLength(wt:WeightedTermString) = {
    WeightedTermString(new String(wt.term.take(maxTokenLength)), wt.weight)
  }

  /** Given some text, builds a list of weighted terms. */
  def makeTerms(textIn: String): Seq[WeightedTermString] = {
    if (!weightedTermRegexes.isDefined) {
      throw new DisplayedError("bad_important_words_pattern")
    }

    // If we start paying attention to Asian languages, switch this to use
    // ICUTokenizer, which is in a separate package. We're mimicking our
    // ElasticSearch behavior here.
    //
    // https://www.elastic.co/guide/en/elasticsearch/guide/current/icu-tokenizer.html
    val normalizedText = Normalizer.normalize(textIn, Normalizer.Form.NFKC)
    val reader = new StringReader(normalizedText)
    val tokenizer = new StandardTokenizer(reader)
    var charTermAttribute = tokenizer.addAttribute(classOf[CharTermAttribute])

    val ret = new ArrayBuffer[WeightedTermString]

    // Docs say reset. http://lucene.apache.org/core/4_8_0/core/index.html?org/apache/lucene/analysis/TokenStream.html
    tokenizer.reset()
    while (tokenizer.incrementToken()) {
      processTerm(charTermAttribute.toString).foreach { weightedTerm =>
        ret.append(limitTermLength(weightedTerm))
      }
    }
    tokenizer.end()
    tokenizer.close()

    ret
  }
}
