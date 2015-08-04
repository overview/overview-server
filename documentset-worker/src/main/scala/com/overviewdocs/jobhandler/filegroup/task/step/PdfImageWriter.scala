package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.RenderedImage
import scala.collection.AbstractIterator
import org.w3c.dom.Node
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import java.io.File

object PdfImageWriter {
  private val StandardMetadataFormat = "javax_imageio_1.0";
  private val InchesPerMm = 25.4

  def writeImage(image: RenderedImage, outputFile: File, imageFormat: String, resolution: Int): Unit = {
    val writers = ImageIO.getImageWritersByFormatName(imageFormat)

    val imageWriter = writers.next()

    val writerParams = imageWriter.getDefaultWriteParam

    val meta = createMetadata(image, imageWriter, writerParams, resolution);

    val output = ImageIO.createImageOutputStream(outputFile)
    imageWriter.setOutput(output)
    imageWriter.write(null, new IIOImage(image, null, meta), writerParams);

    imageWriter.dispose

    output.flush
    output.close

  }

  private def createMetadata(image: RenderedImage, imageWriter: ImageWriter,
                             writerParams: ImageWriteParam, resolution: Int): IIOMetadata =
    {
      val imageType =
        if (writerParams.getDestinationType() != null) writerParams.getDestinationType()
        else ImageTypeSpecifier.createFromRenderedImage(image)

      val meta = imageWriter.getDefaultImageMetadata(imageType, writerParams)

      addResolution(meta, resolution)

    }

  private def addResolution(meta: IIOMetadata, resolution: Int): IIOMetadata = {

    def findOrAppendNode(node: IIOMetadataNode, name: String): IIOMetadataNode =
      findChildNode(node, name).getOrElse {
        val child = new IIOMetadataNode(name)
        node.appendChild(child)
        child
      }

    def setPixelSize(sizeNode: IIOMetadataNode) =
      sizeNode.setAttribute("value", (resolution / InchesPerMm).toString)

    val root = meta.getAsTree(StandardMetadataFormat).asInstanceOf[IIOMetadataNode]

    val dimension = findOrAppendNode(root, "Dimension")

    val horizontalSize = findOrAppendNode(dimension, "HorizontalPixelSize")
    setPixelSize(horizontalSize)

    val verticalSize = findOrAppendNode(dimension, "VerticalPixelSize")
    setPixelSize(verticalSize)

    meta.mergeTree(StandardMetadataFormat, root)

    meta
  }

  private def findChildNode(node: Node, name: String): Option[IIOMetadataNode] = {
    val children = new AbstractIterator[Node] {
      private val nodes = node.getChildNodes
      private var index = -1
      override def hasNext = index < (nodes.getLength - 1)
      override def next() = {
        index += 1
        nodes.item(index)
      }
    }

    children.find(_.getNodeName == name)
      .map(_.asInstanceOf[IIOMetadataNode])
  }

}