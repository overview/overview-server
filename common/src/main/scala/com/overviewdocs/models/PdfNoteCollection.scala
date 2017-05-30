package com.overviewdocs.models

case class PdfNoteCollection(
  val pdfNotes: Array[PdfNote]
) {
  override def toString: String = {
    s"[${pdfNotes.mkString(";")}]"
  }

  override def equals(rhs: Any): Boolean = {
    rhs match {
      case notes: PdfNoteCollection => pdfNotes.sameElements(notes.pdfNotes)
      case _ => false
    }
  }
}
