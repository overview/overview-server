package views.html.DocumentSet

import play.api.i18n.Lang

import org.overviewproject.tree.orm.Tree

class _treeSpec extends views.html.ViewSpecification {
  trait BaseScope extends HtmlViewSpecificationScope {
    def tree: Tree = Tree(
      id=1L,
      documentSetId=2L,
      title="title",
      documentCount=4,
      lang="en",
      suppliedStopWords="",
      importantWords="",
      createdAt=new java.sql.Timestamp(1392218269486L) // Wed Feb 12 10:17:49 EST 2014
    )
    def lang = Lang("en")
    override def result = _tree(tree)(lang)
  }

  "views.html.DocumentSet._tree" should {
    "set data-tree-id" in new BaseScope {
      override def tree = super.tree.copy(id=5L)
      Option($("li").attr("data-tree-id")) must beSome("5")
    }

    "display the title" in new BaseScope {
      override def tree = super.tree.copy(title="foobar")
      $("h6").text() must beEqualTo("foobar")
    }

    "link to the tree in the title" in new BaseScope {
      override def tree = super.tree.copy(id=1L, documentSetId=2L)
      $("h6 a").attr("href") must beEqualTo("/documentsets/2/trees/1")
    }

    "display the date" in new BaseScope {
      // Wed Feb 12 10:17:49 EST 2014
      override def tree = super.tree.copy(createdAt=new java.sql.Timestamp(1392218269486L))
      $(".created-at").text() must beEqualTo("2014-02-12 15:17 UTC")
    }

    "display the language" in new BaseScope {
      override def tree = super.tree.copy(lang="fr")
      $(".lang").attr("data-lang") must beEqualTo("fr")
      $(".lang").text() must beEqualTo("French")
    }

    "display the language name, localized" in new BaseScope {
      // XXX this should be a Magic test, not a test on this view
      override def tree = super.tree.copy(lang="fr")
      override def lang = Lang("fr")
      $(".lang").text() must beEqualTo("français")
    }

    "not display important words when there are none" in new BaseScope {
      override def tree = super.tree.copy(importantWords="")
      $(".important-words").length must beEqualTo(0)
    }

    "display important words when there are some" in new BaseScope {
      override def tree = super.tree.copy(importantWords="one\ntwo three")
      val $el = $(".important-words")
      $el.length must beEqualTo(1)
      $el.text() must beEqualTo("3 important words")
      $el.attr("title") must beEqualTo("one\ntwo\nthree")
    }

    "not display supplied stop words when there are none" in new BaseScope {
      override def tree = super.tree.copy(suppliedStopWords="")
      $(".supplied-stop-words").length must beEqualTo(0)
    }

    "display supplied stop words when there are some" in new BaseScope {
      override def tree = super.tree.copy(suppliedStopWords="one\ntwo three")
      val $el = $(".supplied-stop-words")
      $el.length must beEqualTo(1)
      $el.text() must beEqualTo("3 ignored words")
      $el.attr("title") must beEqualTo("one\ntwo\nthree")
    }
  }
}
