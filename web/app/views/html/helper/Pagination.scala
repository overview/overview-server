package views.html.helper

import play.twirl.api.Html

import models.pagination.PageInfo

object Pagination {
  /** Draws navigation links, a la http://twitter.github.com/bootstrap/components.html#pagination
    *
    * @param pageInfo PageInfo
    * @param pageNumToUrl: Function returning the URL of the specified page
    * @param options Options. Valid keys:
    *                previousText: String to show in "previous" button (default "«")
    *                nextText: Text to show in "next" button (default "»")
    */
  def links(pageInfo: PageInfo, pageNumToUrl: Int => String, options: Map[String,String] = Map.empty) = {
    linksImpl(
      1 + pageInfo.offset / pageInfo.limit,
      Math.ceil(1.0 * pageInfo.total / pageInfo.limit).toInt,
      pageNumToUrl,
      options
    )
  }

  private def linksImpl(pageNum: Int, nPages: Int, pageNumToUrl: Int => String, options: Map[String,String] = Map.empty) = {
    if (pageNum == 1 && nPages == 1) {
      Html("")
    } else {
      val prevText = options.get("previousText").getOrElse("«")
      val nextText = options.get("nextText").getOrElse("»")

      def disabledLi(text: String) = <li class="disabled"><a>{text}</a></li>
      def textLi(url: String, text: String) = <li><a href={url}>{text}</a></li>

      def prevLi = {
        if (pageNum == 1) {
          disabledLi(prevText)
        } else {
          val prevHref = pageNumToUrl(pageNum - 1)
          textLi(prevHref, prevText)
        }
      }

      def nextLi = {
        if (pageNum == nPages) {
          disabledLi(nextText)
        } else {
          val nextHref = pageNumToUrl(pageNum + 1)
          textLi(nextHref, nextText)
        }
      }

      def liFor(n: Int) = {
        if (n == pageNum) {
          <li class="active"><a>{n}</a></li>
        } else {
          <li><a href={pageNumToUrl(n)}>{n}</a></li>
        }
      }

      Html(
        <div class="pagination-outer">
          <ul class="pagination">
            {prevLi}
            {(1 until nPages + 1).map { pageNum =>
              liFor(pageNum)
            }}
            {nextLi}
          </ul>
        </div>.buildString(false))
    }
  }
}
