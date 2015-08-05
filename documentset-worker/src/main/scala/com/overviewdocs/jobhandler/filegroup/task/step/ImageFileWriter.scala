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


/**
 * Helper that writes a [[RenderedImage]] to a [[File]], in the specified `format` and `resolution`
 */
object ImageFileWriter {
  private val StandardMetadataFormat = "javax_imageio_1.0";
  private val InchesPerMm = 25.4

  /**
   * Writes the `image` to the `outputFile`.
   * No compression is attempted, and it's assumed that an [[ImageWriter]] exists for
   * the specified `imageFormat`. Really only tested with png.
   * @param image the image to be saved
   * @param outputFile the file where the image is saved
   * @param imageFormat a string specifying the format, eg. "png"
   * @param resolution the image resolution, in dpi 
   *
   * If the call completes, the file should have been written successfully.
   * @throws exceptions if something goes wrong
   */
  def writeImage(image: RenderedImage, outputFile: File, imageFormat: String, resolution: Int): Unit = {
    val writers = ImageIO.getImageWritersByFormatName(imageFormat)

    val imageWriter = writers.next()

    val writerParams = imageWriter.getDefaultWriteParam

    val metadata = createMetadata(image, imageWriter, writerParams, resolution);

    val output = ImageIO.createImageOutputStream(outputFile)
    imageWriter.setOutput(output)
    imageWriter.write(null, new IIOImage(image, null, metadata), writerParams);

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

      val metadata = imageWriter.getDefaultImageMetadata(imageType, writerParams)

      addResolution(metadata, resolution)

    }

  private def addResolution(metadata: IIOMetadata, resolution: Int): IIOMetadata = {

    def findOrAppendNode(node: IIOMetadataNode, name: String): IIOMetadataNode =
      findChildNode(node, name).getOrElse {
        val child = new IIOMetadataNode(name)
        node.appendChild(child)
        child
      }

    def setPixelSize(sizeNode: IIOMetadataNode) =
      sizeNode.setAttribute("value", (resolution / InchesPerMm).toString)

    val root = metadata.getAsTree(StandardMetadataFormat).asInstanceOf[IIOMetadataNode]

    val dimension = findOrAppendNode(root, "Dimension")

    val horizontalSize = findOrAppendNode(dimension, "HorizontalPixelSize")
    setPixelSize(horizontalSize)

    val verticalSize = findOrAppendNode(dimension, "VerticalPixelSize")
    setPixelSize(verticalSize)

    metadata.mergeTree(StandardMetadataFormat, root)

    metadata
  }

  private def findChildNode(node: Node, name: String): Option[IIOMetadataNode] = {
    val children = new AbstractIterator[Node] {
      private val nodes = node.getChildNodes
      private var index = -1
      override def hasNext = (index + 1) < nodes.getLength 
      override def next() = {
        index += 1
        nodes.item(index)
      }
    }

    children.find(_.getNodeName == name)
      .map(_.asInstanceOf[IIOMetadataNode])
  }

}