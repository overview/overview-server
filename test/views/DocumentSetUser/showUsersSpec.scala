package views.json.DocumentSetUser

import play.api.libs.json.Json.toJson

import org.specs2.mutable.Specification

import org.overviewproject.tree.Ownership
import models.orm.DocumentSetUser

class showUsersSpec extends Specification {

  "Json for showUsers" should {

    "contain user emails" in {
      val email = Seq("user1@host.org", "user2@host.com")
      val documentSetUsers = email.map(DocumentSetUser(1l, _, Ownership.Viewer))
      
      val resultJson = toJson(views.json.DocumentSetUser.showUsers(documentSetUsers)).toString
      resultJson must beMatching(""".*"viewers":\[(.*email.*,?){2}\].*""".r)
    }
  }
}
