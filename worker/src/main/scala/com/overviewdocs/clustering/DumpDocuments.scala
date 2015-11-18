package com.overviewdocs.clustering

/** Debugging app, so we can check why clustering is failing.
  */
object DumpDocuments extends App {
  if (args.length != 1) {
    System.err.println("Usage: DumpDocuments [docset-id]")
    System.exit(1)
  }

  val documents = new CatDocuments(args(0).toLong, None)

  documents.foreach { document =>
    System.out.write(s"${document.id},${document.tokens.mkString(" ")}\n".getBytes("utf-8"))
  }
}
