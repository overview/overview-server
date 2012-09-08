package overview.http

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class BasicAuthSpec extends Specification {

  trait DocWithAuth extends Scope {
    val name = "username"
    val rawPassword = "password"
    val query = "query"

    val docWithAuth = new DocumentAtURL(query) with BasicAuth {
      val username = name
      val password = rawPassword
    }
  }

  "WithBasicAuth" should {

    "contain username and password" in new DocWithAuth {
      docWithAuth.username must be equalTo (name)
      docWithAuth.password must be equalTo (rawPassword)
    }

    "be retrievable from DocumentAtURL" in new DocWithAuth {
      val docAtUrl : DocumentAtURL = docWithAuth

      docAtUrl.textURL must be equalTo(query)

      val r = docAtUrl match {
        case d: DocumentAtURL with BasicAuth => true
        case _ => false
      }

      r must beTrue
    }
  }
  
}
