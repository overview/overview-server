package controllers

import scala.io

import play.api.Play
import play.api.Play.current
import play.api.mvc._
import play.api.libs.iteratee.Enumerator

object Tree extends Controller {
    def root(documentSetId: Long) = Action {
        val file = Play.application.getFile("conf/stub-tree-root.json")
        val json = io.Source.fromFile(file).mkString
        SimpleResult(
            header = ResponseHeader(200, Map(CONTENT_TYPE -> "application/json")),
            body = Enumerator(json)
        )
    }
}
