package views.json.DocumentSetUser

import org.specs2.mutable.Specification

import org.overviewproject.tree.Ownership
import models.orm.DocumentSetUser

class indexSpec extends Specification {

  "Json for index" should {
    "contain user emails" in {
      val email = Seq("user1@host.org", "user2@host.com")
      val documentSetUsers = email.map(DocumentSetUser(1l, _, Ownership.Viewer))
      
      val resultJson = views.json.DocumentSetUser.index(documentSetUsers).toString
      resultJson must beMatching("""\s*\{\s*"viewers":\[(.*"email":.*){2}\].*\}\s*""".r)
    }

    "show an empty list" in {
      val resultJson = views.json.DocumentSetUser.index(Seq()).toString
      resultJson must beEqualTo("""{"viewers":[]}""")
    }
  }
}
