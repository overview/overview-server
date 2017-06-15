// Used by clients to check if a file already exists in document set, via sha1 hash
// HEAD /document-sets/files/[sha1]
// returns status 204 if that file was previously added, 404 otherwise
//
// Document set id is implicit in api token used for auth. One file may produce multiple documents (e.g. split pages)

package controllers.api

import javax.inject.Inject
import play.api.mvc.Action
import play.api.libs.concurrent.Execution.Implicits._

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.anyUser
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.DocumentSetFileBackend
import controllers.auth.ApiAuthorizedAction

class DocumentSetHashController @Inject() (
  backend: DocumentSetFileBackend
) extends ApiController {

  // This code copied from DocumentSetFileController.scala, boo
  def head(sha1: Array[Byte]) = ApiAuthorizedAction(anyUser).async { request =>

    backend.existsByIdAndSha1(request.apiToken.documentSetId.get, sha1).map(_ match {
      case true => NoContent
      case false => NotFound
    })
  }
}
