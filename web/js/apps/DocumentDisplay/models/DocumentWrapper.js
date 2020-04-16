export default {
  /**
   * Returns { id, displayType, displayUrl, pdfNotes, isFromOcr, rootFile, fullDocumentInfo }
   *
   * Arguments:
   *
   * * document: a { id, url } document.
   */
  wrap(document, urlPropertiesExtractor) {
    if (!document) {
      return {
        id: null,
        displayType: null,
        displayUrl: null,
        pdfNotes: null,
        isFromOcr: null,
        fullDocumentInfo: null,
      }
    }

    return Object.assign({
      id: document.id,
      pdfNotes: document.pdfNotes,
      isFromOcr: document.isFromOcr,
      rootFile: document.rootFile,
      fullDocumentInfo: document.fullDocumentInfo,
    }, urlPropertiesExtractor.urlToProperties(document.url))
  }
}
