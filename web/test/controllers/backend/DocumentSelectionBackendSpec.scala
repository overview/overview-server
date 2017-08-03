package controllers.backend

import com.google.re2j.Pattern
import org.specs2.mutable.Specification

import com.overviewdocs.query.{Field,Query,AndQuery,OrQuery,NotQuery,RegexQuery,PhraseQuery}
import models.SelectionWarning

// See also: DbDocumentSelectionBackendSpec for stuff that interacts with the DB
class DocumentSelectionBackendSpec extends Specification {
  private def AND(children: Query*) = AndQuery(children.toVector)
  private def OR(children: Query*) = OrQuery(children.toVector)
  private def NOT(child: Query) = NotQuery(child)
  private def PHRASE(field: Field, s: String): Query = PhraseQuery(field, s)
  private def REGEX(field: Field, s: String): Query = RegexQuery(field, s)

  private def rule(field: Field, patternString: String, negated: Boolean) = {
    DocumentSelectionBackend.RegexSearchRule(field, Pattern.compile(patternString), negated)
  }

  private def grokRules(query: Query) = DocumentSelectionBackend.queryToRegexSearchRules(query)

  "DocumentBackend" should {
    "queryToRegexSearchRules" should {
      "grab a top-level regex" in {
        grokRules(REGEX(Field.All, "regex")) must beEqualTo((
          Vector(rule(Field.All, "regex", false)),
          Nil
        ))
      }

      "report Pattern.compile exception as a warning" in {
        grokRules(REGEX(Field.All, "re(gex")) must beEqualTo((
          Vector(),
          List(SelectionWarning.RegexSyntaxError("re(gex", "missing closing )", -1))
        ))
      }

      "grab a top-level NOT regex" in {
        grokRules(NOT(REGEX(Field.All, "regex"))) must beEqualTo((
          Vector(rule(Field.All, "regex", true)),
          Nil
        ))
      }

      "grab AND-ed regexes" in {
        grokRules(AND(REGEX(Field.All, "regex"), REGEX(Field.Title, "title"))) must beEqualTo((
          Vector(rule(Field.All, "regex", false), rule(Field.Title, "title", false)),
          Nil
        ))
      }

      "ignore non-regex search" in {
        grokRules(PHRASE(Field.All, "s")) must beEqualTo((Vector(), Nil))
      }

      "ignore non-regex search in AND-ed regexes" in {
        grokRules(AND(REGEX(Field.All, "regex"), PHRASE(Field.Title, "title"))) must beEqualTo((
          Vector(rule(Field.All, "regex", false)),
          Nil
        ))
      }

      "turn OR-ed regexes into warnings" in {
        grokRules(OR(REGEX(Field.All, "regex"), PHRASE(Field.Title, "title"))) must beEqualTo((
          Vector(),
          List(SelectionWarning.NestedRegexIgnored("regex"))
        ))
      }

      "allow some regexes and some warnings in the same query" in {
        grokRules(AND(REGEX(Field.Title, "title"), OR(NOT(REGEX(Field.All, "err1")), REGEX(Field.All, "err2")))) must beEqualTo((
          Vector(rule(Field.Title, "title", false)),
          List(SelectionWarning.NestedRegexIgnored("err1"), SelectionWarning.NestedRegexIgnored("err2"))
        ))
      }
    }
  }
}
