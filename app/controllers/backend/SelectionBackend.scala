package controllers.backend

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

import models.{Selection,SelectionRequest}

/** A store of Selections.
  *
  * A selection maps a SelectionRequest to a Seq[Long] of Document IDs at a
  * given point in time. We need selections to stick around a bit so we can
  * paginate over them, even when time moves forward.
  *
  * Think of this as a cache. If you showOrCreate() on a request that refers
  * back to a previously-cached selection, you'll get the cached selection
  * back.
  *
  * How do we know the caller wants the cached selection? Well, that's outside
  * the scope of this backend. Call `show()` if you <em>must</em> use a cached
  * Selection, `create()` if you <em>must</em> build a new Selection (i.e., you
  * want your search results freshened) or `showOrCreate()` if you want to use
  * an existing one if it's present or create a new one otherwise. If you want
  * more complicated logic, figure it out yourself, using `show()` and
  * `create()` as building blocks.
  */
trait SelectionBackend extends Backend {
  /** Converts a SelectionRequest to a new Selection.
    *
    * This involves calling the DocumentIdFinder.
    */
  def create(request: SelectionRequest): Future[Selection]

  /** Does the grunt work for the create() method. */
  protected def findDocumentIds(request: SelectionRequest): Future[Seq[Long]]
}

trait NullSelectionBackend extends SelectionBackend {
  override def create(request: SelectionRequest) = {
    findDocumentIds(request).map(Selection(request, _))
  }
}
