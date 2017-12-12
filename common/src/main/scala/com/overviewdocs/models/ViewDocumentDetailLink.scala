package com.overviewdocs.models

/** A link created by a plugin to prompt to show an iframe.
  *
  * A ViewDocumentDetailPopupLink will be rendered near the document when
  * the user views it. Clicking will open an iframe to `url`.
  */
case class ViewDocumentDetailLink(
  /** URL where the popup should open.
    *
    * The phrase :documentId will be replaced by the document ID. Also, Overview
    * will always add `apiToken` and `server` query parameters.
    */
  url: String,

  /** Title of the link, used during rendering. */
  title: String,

  /** Text of the link, used during rendering. */
  text: String,

  /** Icon of the link, used during rendering.
    *
    * Browse possible icons at https://fontawesome.com/icons.
    */
  iconClass: String,
)
