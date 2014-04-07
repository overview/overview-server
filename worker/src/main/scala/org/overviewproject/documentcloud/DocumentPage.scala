package org.overviewproject.documentcloud

class DocumentPage(document: Document, pageNum: Int) extends 
Document(
    s"${document.id}#p$pageNum", 
    document.title, 
    1,
    document.access, document.textUrl, document.pageUrlTemplate) {
  
  private val PagePattern = "{page}"
    
  override val url: String = document.pageUrlTemplate.replace(PagePattern, s"$pageNum")
  override val pageNumber = Some(pageNum)
}