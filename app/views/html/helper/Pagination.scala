package views.html.helper

import play.twirl.api.Html

import org.overviewproject.tree.orm.finders.ResultPageDetails

object Pagination {
  /** Draws navigation links, a la http://twitter.github.com/bootstrap/components.html#pagination
    *
    * @param pageDetails Details from the ResultPage
    * @param pageNumToUrl: Function returning the URL of the specified page
    * @param options Options. Valid keys:
    *                previousText: String to show in "previous" button (default "«")
    *                nextText: Text to show in "next" button (default "»")
    */
  def links(pageDetails: ResultPageDetails, pageNumToUrl : Int => String, options: Map[String, String] = Map.empty) = {
    if (pageDetails.isOnlyPage) {
      Html("")
    } else {
      val prevText = options.get("previousText").getOrElse("«")
      val nextText = options.get("nextText").getOrElse("»")

      def disabledLi(text: String) = <li class="disabled"><a>{text}</a></li>
      def textLi(url: String, text: String) = <li><a href={url}>{text}</a></li>

      def prevLi = {
        if (pageDetails.isFirst) {
          disabledLi(prevText)
        } else {
          val prevHref = pageNumToUrl(pageDetails.pageNum - 1)
          textLi(prevHref, prevText)
        }
      }

      def nextLi = {
        if (pageDetails.isLast) {
          disabledLi(nextText)
        } else {
          val nextHref = pageNumToUrl(pageDetails.pageNum + 1)
          textLi(nextHref, nextText)
        }
      }

      def liFor(n: Int) = {
        if (n == pageDetails.pageNum) {
          <li class="active"><a>{n}</a></li>
        } else {
          <li><a href={pageNumToUrl(n)}>{n}</a></li>
        }
      }

      Html(
        <div class="pagination-outer">
          <ul class="pagination">
            {prevLi}
            {(1 until pageDetails.lastPageNum + 1).map { pageNum =>
              liFor(pageNum)
            }}
            {nextLi}
          </ul>
        </div>.buildString(false))
    }
  }
}
