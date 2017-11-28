package controllers.api

class ApiTokenControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val controller = new ApiTokenController(fakeControllerComponents)
  }

  "show" should {
    trait ShowScope extends BaseScope {
      override def action = controller.show()
    }

    // This is dead simple: the auth framework already populates
    // request.apiToken. There's no backend at all, and there's no need
    // to check whether the apiToken exists because we already have it.
    //
    // (If the user users the wrong token, the auth framework will return
    // a "Not Authorized" error, which is what we want anyway.)

    "show the ApiToken" in new ShowScope {
      status(result) must beEqualTo(OK)
      contentType(result) must beSome("application/json")
      val json = contentAsString(result)

      json must /("token" -> apiToken.token)
      json must /("userEmail" -> apiToken.createdBy)
      json must /("documentSetId" -> apiToken.documentSetId.get.toInt)
    }
  }
}
