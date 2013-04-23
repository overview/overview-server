package org.overviewproject.documentcloud

class DocumentPage(document: Document, pageNum: Int) extends 
Document(
    s"${document.id}#p$pageNum", 
    s"${document.title} p.$pageNum", 
    document.access, document.pageUrlTemplate) {
  
  private val PagePattern = "{page}"
    
  override val url: String = document.pageUrlTemplate.replace(PagePattern, s"$pageNum")
}