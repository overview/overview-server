import { isEqual } from 'underscore'
import Views from '../collections/Views'
import Tags from '../collections/Tags'
import DocumentList from './DocumentList'
import DocumentListParams from './DocumentListParams'

# Tracks global state.
#
# * Provides `documentSet` and `transactionQueue`: constants.
# * Gives access to `view`, `documentList`, `document`: global state as
#   Backbone.Model attributes.
#
# Usage:
#
#     transactionQueue = new TransactionQueue()
#     documentSet = new DocumentSet(...)
#     state = new State({}, documentSet: documentSet, transactionQueue: transactionQueue)
#     state.once('sync', -> renderEverything())
#     state.init()
export default class State extends Backbone.Model
  defaults:
    # The currently displayed View.
    view: null

    # The currently displayed list of documents.
    #
    # This is a partially-loaded list. Callers can read its `.params`,
    # `.length`, `.documents`; they can .tag() and .untag() it.
    #
    # Alter this list through `.setDocumentListParams()`.
    documentList: null

    # The currently-displayed document.
    #
    # This is *always* a member of `documentList`.
    #
    # When document is set, tagging/untagging applies to this document. When
    # document is null, tagging/untagging applies to documentList.
    document: null

  initialize: (attributes, options={}) ->
    throw 'Must pass options.documentSet, a DocumentSet' if !options.documentSet
    throw 'Must pass options.transactionQueue, a TransactionQueue' if !options.transactionQueue

    @documentSet = options.documentSet
    @documentSetId = @documentSet.id
    @transactionQueue = options.transactionQueue

    view = attributes.view || @documentSet.views.at(0)

    @set
      view: view
      document: null
      documentList: new DocumentList {},
        documentSet: @documentSet
        transactionQueue: @transactionQueue
        params: {}

  # Sets new documentList params and unsets document.
  #
  # This is the only safe way to change document lists.
  #
  # You may pass a DocumentListParams instance, or you may pass a plain JSON
  # object that will be passed to the DocumentListParams constructor.
  #
  # Pass `true` as the `reverse` argument to flip the order of documents in
  # the DocumentList.
  setDocumentListParams: (options, reverse=false) ->
    oldParams = @get('documentList')?.params
    oldReverse = @get('documentList')?.reverse || false

    params = DocumentListParams.normalize(options)
    return if isEqual(oldParams, params) && oldReverse == reverse

    @set
      document: null
      documentList: new DocumentList {},
        documentSet: @documentSet
        transactionQueue: @transactionQueue
        params: params,
        reverse: reverse

  # Sets new documentList params relative to the current ones.
  #
  # Example:
  #
  #     state.setDocumentListParams(tags: { ids: [ 1, 2 ] })
  #     state.refineDocumentListParams(q: 'foo')
  #     state.refineDocumentListParams(q: null)
  #     state.refineDocumentListParams(reverse: true)
  refineDocumentListParams: (options, reverse) ->
    oldParams = @get('documentList')?.params || {}
    newParams = DocumentListParams.extend(oldParams, options)

    if !reverse? && newParams.sortByMetadataField == oldParams.sortByMetadataField
      # Leaving sort field unchanged, and reverse is unset? Then
      # don't modify reverse.
      reverse = @get('documentList')?.reverse || false

    @setDocumentListParams(newParams, reverse)

  # Switches to a new View.
  #
  # This is the correct way of calling .set('view', ...). The reason: we
  # need to update documentList to point to the new view.
  setView: (view) ->
    reset = => @set(view: view)

    @stopListening(@get('view'))

    if view?.get('type') == 'job'
      @listenToOnce(view, 'change:type', reset)

    reset()

  # Returns the thing Tagging operations should apply to.
  #
  # If there is a document, it's that. Othewise, it's the documentList.
  #
  # Usage:
  #
  #   state.getCurrentTaggable()?.tag(tag)
  getCurrentTaggable: ->
    @get('document') || @get('documentList') || null
