package com.overviewdocs.documentcloud

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.DocumentCloudImport
import com.overviewdocs.models.tables.{Documents,DocumentCloudImportIdLists}

/** A to-do list: it produces all the headers we need to fetch.
  */
class HeaderProducer(dcImport: DocumentCloudImport) extends HasDatabase {
  // The stuff in DocumentCloudImport we care about
  private val documentSetId = dcImport.documentSetId
  private val dcImportId = dcImport.id
  private val splitPages = dcImport.splitPages
  private val nIdLists = dcImport.nIdListsTotal.get

  val username = dcImport.username
  val password = dcImport.password

  // set during init
  private var existingDocumentIds: Set[Long] = Set()
  private var nextDocumentId = dcImport.documentSetId << 32

  case class IdListRowAndPage(row: IdListRow, pageBase1: Option[Int]) {
    def toHeader(id: Long) = DocumentCloudDocumentHeader(
      id,
      documentSetId,
      pageBase1.fold(row.documentCloudId)(n => s"${row.documentCloudId}#p${n}"),
      row.title,
      pageBase1,
      pageBase1.fold(row.fullTextUrl)(n => row.pageTextUrlTemplate.replace("{page}", n.toString)),
      row.access
    )
  }

  /*
   * Thread-safety time! Only access these variables in synchronized blocks,
   * and maintain invariants:
   *
   * * `state==Normal`:
   *   * `currentFetch.isCompleted`
   *   * We don't know if `hangingHeader` is set
   *   * We don't know what `currentIterator.hasNext` will return
   * * `state==NeedIdList`:
   *   * `nIdLists > nextIdListNumber`
   *   * `!currentIterator.hasNext`
   *   * `currentFetch.isCompleted`
   *   * `hangingHeader.isEmpty`
   * * `state==FetchingIdList`:
   *   * `nIdLists > nextIdListNumber`
   *   * `hangingHeader.isEmpty`
   *   * Callers can wait on `currentFetch`
   * * `state==End`:
   *   * `nIdLists == nextIdListNumber`
   *   * `hangingHeader.isEmpty`
   */
  private var hangingHeader: Option[DocumentCloudDocumentHeader] = None
  private var nextIdListNumber: Int = 0
  private var currentIterator: Iterator[IdListRowAndPage] = Iterator()
  private var currentFetch: Future[Unit] = Future.successful(())
  sealed trait State
  object State {
    case object Normal extends State
    case object NeedIdList extends State
    case object FetchingIdList extends State
    case object End extends State
  }
  private var state: State = State.NeedIdList

  private lazy val initialize: Future[Unit] = fetchExistingDocumentIds

  private def fetchExistingDocumentIds: Future[Unit] = {
    import database.api._

    for {
      ids <- database.seq(Documents.filter(_.documentSetId === dcImport.documentSetId).map(_.id))
    } yield {
      existingDocumentIds = ids.toSet
    }
  }

  /** Returns a Skip or Fetch result, based on what's in the iterator.
    *
    * Assumes hangingHeader is `None`. May set hangingHeader as a side-effect.
    * In other words, this touches the invariants: call it synchronously.
    */
  private def nextResultFromCurrentIterator: HeaderProducer.Result = {
    var nSkip: Int = 0
    var maybeHeader: Option[DocumentCloudDocumentHeader] = None

    while (maybeHeader.isEmpty && currentIterator.hasNext) {
      val nextRow = currentIterator.next
      if (existingDocumentIds.contains(nextDocumentId)) {
        nSkip += 1
      } else {
        maybeHeader = Some(nextRow.toHeader(nextDocumentId))
      }
      nextDocumentId += 1
    }

    if (nSkip > 0) {
      hangingHeader = maybeHeader
      HeaderProducer.Skip(nSkip)
    } else {
      HeaderProducer.Fetch(maybeHeader.get)
    }
  }

  private lazy val fetchIdListStringCompiled = {
    import database.api._
    Compiled { (importId: Rep[Int], idListNumber: Rep[Int]) =>
      DocumentCloudImportIdLists
        .filter(il => il.documentCloudImportId === importId && il.pageNumber === idListNumber)
        .map(_.idsString)
    }
  }

  private def buildIterator(idList: IdList): Iterator[IdListRowAndPage] = {
    if (splitPages) {
      buildSplitPagesIterator(idList)
    } else {
      buildNoSplitPagesIterator(idList)
    }
  }

  private def buildSplitPagesIterator(idList: IdList): Iterator[IdListRowAndPage] = {
    idList.rows
      .view
      .map { row =>
        if (row.nPages == 1) {
          Seq(IdListRowAndPage(row, None))
        } else {
          Iterator.tabulate(row.nPages)(n => IdListRowAndPage(row, Some(n + 1)))
        }
      }
      .flatten
      .toIterator
  }

  private def buildNoSplitPagesIterator(idList: IdList): Iterator[IdListRowAndPage] = {
    idList.rows
      .view
      .map { row => IdListRowAndPage(row, None) }
      .toIterator
  }

  private def fetchIdList(idListNumber: Int): Future[IdList] = {
    // We're being called within a synchronized block.
    database.option(fetchIdListStringCompiled(dcImportId, idListNumber)).map(_ match {
      case Some(idsString) => IdList.decode(idsString).getOrElse(IdList(Seq()))
      case None => IdList(Seq())
    })
  }

  private def innerNext: Future[HeaderProducer.Result] = synchronized {
    // This is where we maintain our invariants. When this method returns,
    // all our conditions must be preserved.
    //
    // Two notes:
    //
    // * Future.successsful() is synchronous; Future() is not. Whatever's
    //   inside a Future() must not alter our invariants' variables.
    // * We loop around. 
    state match {
      case State.Normal => {
        hangingHeader match {
          case Some(header) => {
            hangingHeader = None
            Future.successful(HeaderProducer.Fetch(header))
          }
          case None => {
            if (currentIterator.hasNext) {
              Future.successful(nextResultFromCurrentIterator)
            } else {
              if (nIdLists == nextIdListNumber) {
                state = State.End
              } else {
                state = State.NeedIdList
              }
              innerNext
            }
          }
        }
      }
      case State.NeedIdList => {
        val future: Future[Unit] = fetchIdList(nextIdListNumber).map { idList =>
          synchronized {
            currentIterator = buildIterator(idList)
            nextIdListNumber += 1
            if (state != State.End) state = State.Normal
          }
        }

        state = State.FetchingIdList
        currentFetch = future
        innerNext
        // Now we unlock. Callers who enter asynchronously after thiss will wait
        // on currentFetch and then re-enter innerNext; they won't alter any
        // invariants.
      }
      case State.FetchingIdList => currentFetch.flatMap(_ => innerNext)
      case State.End => Future.successful(HeaderProducer.End)
    }
  }

  def stop: Unit = synchronized {
    state = State.End
  }

  def next: Future[HeaderProducer.Result] = {
    if (state == State.End) {
      Future.successful(HeaderProducer.End)
    } else {
      for {
        _ <- initialize
        result <- innerNext
      } yield result
    }
  }
}

object HeaderProducer {
  sealed trait Result

  /** There are no more headers. */
  case object End extends Result

  /** `n` documents are next in line, but we already inserted them in the
    * database. Update internal state, then call next() again.
    */
  case class Skip(n: Int) extends Result

  /** Here's a document we need to fetch and write.
    */
  case class Fetch(header: DocumentCloudDocumentHeader) extends Result
}
