package org.overviewproject.documentcloud

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class SearchResultSpec extends Specification {
    
  "SearchResult" should {

    trait SearchContext extends Scope {
      val total = 29
      val document1: Document = Document("dc id", "title", 1, "public", "text-url", "http://document-p{page}")
      val pageNum = 1
    }
    
    "convert from json" in new SearchContext {
      val result = ConvertSearchResult(jsonSearchResult(total, pageNum, document1))
    		 
      result.total must be equalTo(total)
      result.page must be equalTo(pageNum)
      result.documents must haveSize(2)
      
      result.documents.head must be equalTo(document1)
    }
  }

  def jsonSearchResult(total: Int, pageNum: Int, d: Document): String = s"""
{
  "total": $total,
  "page": $pageNum,
  "per_page": 2,
  "q": "giraffe",
  "documents": [
    {
      "id": "${d.id}",
      "title": "${d.title}",
      "access": "${d.access}",
      "pages": 1,
      "description": null,
      "source": "Cleveland Metroparks Zoo",
      "created_at": "Fri, 15 Feb 2013 19:23:24 +0000",
      "updated_at": "Wed, 20 Feb 2013 01:00:03 +0000",
      "canonical_url": "http://documentcloud.org/",
      "resources": {
        "pdf": "https://s3.amazonaws.com/s3.documentcloud.org/documents/604056/circleofwildlifefigureinven.pdf",
        "text": "${d.textUrl}",
        "thumbnail": "https://s3.amazonaws.com/s3.documentcloud.org/documents/604056/pages/circleofwildlifefigureinven-p1-thumbnail.gif",
        "search": "https://www.documentcloud.org/documents/604056/search.json?q={query}",
        "print_annotations": "https://www.documentcloud.org/notes/print?docs[]=604056",
        "page": {
          "text": "${d.pageUrlTemplate}",
          "image": "https://s3.amazonaws.com/s3.documentcloud.org/documents/604056/pages/circleofwildlifefigureinven-p{page}-{size}.gif"
        }
      }
    },
    {
      "id": "598455-rnc-nab-11-1-12-11-6-12-13518689817793-pdf",
      "title": "RNC NAB 11-1-12-11-6-12 (13518689817793).pdf",
      "access": "public",
      "pages": 2,
      "description": null,
      "source": "FCC",
      "created_at": "Thu, 07 Feb 2013 21:30:25 +0000",
      "updated_at": "Thu, 07 Feb 2013 21:31:02 +0000",
      "canonical_url": "http://www.documentcloud.org/documents/598455-rnc-nab-11-1-12-11-6-12-13518689817793-pdf.html",
      "resources": {
        "pdf": "https://s3.amazonaws.com/s3.documentcloud.org/documents/598455/rnc-nab-11-1-12-11-6-12-13518689817793-pdf.pdf",
        "text": "https://s3.amazonaws.com/s3.documentcloud.org/documents/598455/rnc-nab-11-1-12-11-6-12-13518689817793-pdf.txt",
        "thumbnail": "https://s3.amazonaws.com/s3.documentcloud.org/documents/598455/pages/rnc-nab-11-1-12-11-6-12-13518689817793-pdf-p1-thumbnail.gif",
        "search": "https://www.documentcloud.org/documents/598455/search.json?q={query}",
        "print_annotations": "https://www.documentcloud.org/notes/print?docs[]=598455",
        "page": {
          "text": "https://www.documentcloud.org/documents/598455/pages/rnc-nab-11-1-12-11-6-12-13518689817793-pdf-p{page}.txt",
          "image": "https://s3.amazonaws.com/s3.documentcloud.org/documents/598455/pages/rnc-nab-11-1-12-11-6-12-13518689817793-pdf-p{page}-{size}.gif"
        }
      }
    }
  ]
}        
        """
}