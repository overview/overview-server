package com.overviewdocs.clustering

import java.io.{BufferedReader,InputStreamReader}
import java.nio.charset.StandardCharsets
import scala.collection.mutable

import com.overviewdocs.nlp.DocumentVectorTypes.{DocumentID,DocumentSetVectors,DocumentVector}

/** Clusters a bunch of documents.
  *
  * Called with three arguments:
  *
  * * `lang`: Two-letter language code -- for instance, `en`.
  * * `suppliedStopWords`: Stopwords to add to those provided by `lang`,
  *                        space-separated.
  * * `importantWords`: Terms to tweak clustering, space-separated.
  *
  * Expects on standard input: a list of tokenized documents, like so:
  *
  *     123,term1 term2 term3 term4 term1 term3
  *     234,term1 term3 term5 term1
  *     ...
  *
  * This app will process the documents as they come in, inserting them into its
  * internal data structures.
  *
  * When stdin is closed, this app will begin clustering. It will yield progress
  * reports on stdout, like so:
  *
  *     0.001
  *     0.002
  *     0.040
  *     ...
  *
  * The final such message will be `clustering 1.0`
  *
  * After that come document keywords, which will look like this:
  *
  *     5 DOCUMENTS
  *     234,term1_term3 term5
  *     123,term1_term2 term3
  *     ...
  *
  * These will be output in an arbitrary order; we guarantee the number of
  * output documents is equal to the number of input documents.
  *
  * Then come nodes, which will look like this:
  *
  *     2 NODES
  *     0,,f,term1_term2 term4,123 234 ...
  *     1,0,t,term1_term3 term5,123 ...
  *
  * That's a CSV with the following columns:
  *
  * * `id` A numeric ID, incrementing from `0`.
  * * `parentId` The ID of the parent. (The `0` node doesn't have a parent).
  * * `isLeaf` If this is a leaf node, `t`; otherwise, `f`.
  * * `description` The terms in the node.
  * * `documentIds` A space-separated list of document IDs in the node.
  *
  * All text is encoded as UTF-8.
  *
  * There are no errors (assuming valid input). If there are 0 documents, the
  * last line of the program will be `NODES` (newline-terminated). If the input
  * is ill-formed, the program will crash spectacularly.
  *
  * Also, beware of `OutOfMemoryError`. It can happen. Fiddle with `-Xmx` to
  * make it less likely.
  */
object Main extends App {
  val lang: String = args(0)
  val suppliedStopWords: Seq[String] = tokenize(args(1))
  val importantWords: Seq[String] = tokenize(args(2))

  private def tokenize(s: String): Seq[String] = {
    s.split("[ \t\n\r\u00A0]+").filter(_.nonEmpty)
  }

  val indexer = new DocumentSetIndexer(lang, suppliedStopWords, importantWords)

  val stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
  def indexOneLine: Boolean = {
    Option(stdin.readLine) match {
      case None => false
      case Some(line) => {
        val i = line.indexOf(',')
        val documentId = line.substring(0, i).toLong
        val tokensString = line.substring(i + 1)
        indexer.addDocument(documentId, tokensString)
        true
      }
    }
  }
  while (indexOneLine) {}
  stdin.close

  var lastProgress = 0.0
  private def onProgress(progress: Double): Unit = {
    if (progress - lastProgress > 0.01) {
      System.out.println(progress)
      lastProgress = progress
    }
  }

  val (documentVectors, nodes) = indexer.cluster(onProgress)

  writeDocuments(documentVectors)
  writeNodes(nodes)

  private def writeDocuments(documentVectors: DocumentSetVectors): Unit = {
    System.out.println(s"${documentVectors.size} DOCUMENTS")
    documentVectors.foreach { case (id: DocumentID, vector: DocumentVector) =>
      System.out.println(s"$id,${SuggestedTags.suggestedTagsForDocument(vector, documentVectors)}")
    }
  }

  private def writeNodes(rootNode: DocTreeNode): Unit = {
    System.out.println(s"${rootNode.nNodes} NODES")

    // levelorder() from https://en.wikipedia.org/wiki/Tree_traversal
    val queue: mutable.Queue[(Option[Long],DocTreeNode)] = mutable.Queue((None,rootNode))
    var nodeId = 0

    while (!queue.isEmpty) {
      val (parentId, node) = queue.dequeue

      val sb = new StringBuilder()
        .append(nodeId)
        .append(',')
        .append(parentId.map(_.toString).getOrElse(""))
        .append(',')
        .append(if (node.children.isEmpty) 't' else 'f')
        .append(',')
        .append(node.description)
        .append(',')
        .append(node.docs.toArray.sorted.mkString(" "))

      System.out.println(sb.toString)

      node.orderedChildren.foreach { subNode => queue.enqueue((Some(nodeId), subNode)) }
      nodeId += 1
    }
  }
}
