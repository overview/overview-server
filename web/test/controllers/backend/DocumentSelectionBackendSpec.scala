package controllers.backend

import com.google.re2j.Pattern
import org.specs2.mutable.Specification
import play.api.libs.json.Json

import com.overviewdocs.models.{PdfNote,PdfNoteCollection}
import com.overviewdocs.query.{Field,Query,AndQuery,OrQuery,NotQuery,RegexQuery,PhraseQuery}
import com.overviewdocs.test.factories.{PodoFactory=>factory}
import models.SelectionWarning

// See also: DbDocumentSelectionBackendSpec for stuff that interacts with the DB
class DocumentSelectionBackendSpec extends Specification {
  private def AND(children: Query*) = AndQuery(children.toVector)
  private def OR(children: Query*) = OrQuery(children.toVector)
  private def NOT(child: Query) = NotQuery(child)
  private def PHRASE(field: Field, s: String): Query = PhraseQuery(field, s)
  private def REGEX(field: Field, s: String): Query = RegexQuery(field, s)

  private def rule(field: Field, patternString: String, negated: Boolean = false) = {
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

    "RegexSearchRule" should {
      "matches" should {
        "hit on title" in {
          rule(Field.Title, "match me").matches(factory.document(title="foo match me mar")) must beEqualTo(true)
        }

        "miss on title" in {
          rule(Field.Title, "match me").matches(factory.document(title="foo matchXme mar")) must beEqualTo(false)
        }

        "negate match" in {
          rule(Field.Title, "match me", true).matches(factory.document(title="foo match me mar")) must beEqualTo(false)
        }

        "hit on text" in {
          rule(Field.Text, "match me").matches(factory.document(text="foo match me mar")) must beEqualTo(true)
        }

        "miss on text" in {
          rule(Field.Text, "match me").matches(factory.document(text="foo matchXme mar")) must beEqualTo(false)
        }

        "hit Field.All on title" in {
          rule(Field.All, "match me").matches(factory.document(title="foo match me mar")) must beEqualTo(true)
        }

        "miss Field.All on title" in {
          rule(Field.All, "match me").matches(factory.document(title="foo matchXme mar")) must beEqualTo(false)
        }

        "hit Field.All on text" in {
          rule(Field.All, "match me").matches(factory.document(text="foo match me mar")) must beEqualTo(true)
        }

        "miss Field.All on text" in {
          rule(Field.All, "match me").matches(factory.document(text="foo matchXme mar")) must beEqualTo(false)
        }

        "hit a metadata field" in {
          rule(Field.Metadata("foo"), "match me").matches(factory.document(metadataJson=Json.obj("foo" -> "match me"))) must beEqualTo(true)
        }

        "miss a metadata field" in {
          rule(Field.Metadata("foo"), "match me").matches(factory.document(metadataJson=Json.obj("foo" -> "matchXme"))) must beEqualTo(false)
        }

        "not hit when a different metadata field matches" in {
          rule(Field.Metadata("foo"), "match me").matches(factory.document(metadataJson=Json.obj("foo1" -> "match me"))) must beEqualTo(false)
        }

        "not hit Field.All on metadata" in {
          rule(Field.All, "match me") matches(factory.document(metadataJson=Json.obj("foo" -> "match me"))) must beEqualTo(false)
        }

        "hit a note" in {
          val note = PdfNote(2, 3, 4, 5, 6, "match me")
          val pdfNotes = PdfNoteCollection(Array(note))
          rule(Field.Notes, "match me") matches(factory.document(pdfNotes=pdfNotes)) must beEqualTo(true)
        }

        "miss a note" in {
          val note = PdfNote(2, 3, 4, 5, 6, "matchXme")
          val pdfNotes = PdfNoteCollection(Array(note))
          rule(Field.Notes, "match me") matches(factory.document(pdfNotes=pdfNotes)) must beEqualTo(false)
        }
      }
    }
  }
}
