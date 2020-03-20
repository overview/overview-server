export default {
  /**
   * Returns { id, displayType, displayUrl, pdfNotes, isFromOcr, rootFile }
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
      }
    }

    return Object.assign({
      id: document.id,
      pdfNotes: document.pdfNotes,
      isFromOcr: document.isFromOcr,
      rootFile: document.rootFile,
    }, urlPropertiesExtractor.urlToProperties(document.url))
  }
}
