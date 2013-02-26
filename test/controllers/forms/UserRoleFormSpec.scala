package controllers.forms

import org.specs2.mutable.Specification
import models.orm.DocumentSetUserRoleType._

class UserRoleFormSpec extends Specification {

  "UserRoleForm" should {
    
    "create a DocumentSetUser" in {
      val form = UserRoleForm(1l).bind(Map("email" -> "user@host.com", "role" -> "Viewer"))
      
      val documentSetUser = form.get
      documentSetUser.documentSetId must be equalTo(1l)
      documentSetUser.userEmail must be equalTo("user@host.com")
      documentSetUser.role.value must be equalTo(Viewer.value)
    }
  }
}