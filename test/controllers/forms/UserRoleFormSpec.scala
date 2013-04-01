package controllers.forms

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.data.Form

import org.overviewproject.tree.Ownership
import models.orm.DocumentSetUser

class UserRoleFormSpec extends Specification {

  "UserRoleForm" should {
    
    trait FormContext extends Scope {
      def bindForm(email: String, role: String): Form[DocumentSetUser] =
        UserRoleForm(1l).bind(Map("email" -> email, "role" -> role))
    }
    
    "create a DocumentSetUser" in new FormContext {
      val form = bindForm("user@host.com", "Viewer")
      
      val documentSetUser = form.get
      documentSetUser.documentSetId must be equalTo(1l)
      documentSetUser.userEmail must be equalTo("user@host.com")
      documentSetUser.role must be equalTo(Ownership.Viewer)
    }
    
    "reject bad email format" in new FormContext {
      val form = bindForm("bad email format", "Viewer")
      
      form.error("email") must beSome.like { case e => e.message must be equalTo ("error.email") }
    }
    
    
   "reject invalid role strings" in new FormContext {
     val form = bindForm("user@host.com", "Not a Role")
     
     form.error("role") must beSome.like { case e => e.message must be equalTo( "role.invalid_role") }
   }
  }
}
