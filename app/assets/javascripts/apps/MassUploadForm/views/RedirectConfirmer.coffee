define [
  'backbone',
  'i18n'
  'bootstrap-modal'
], (Backbone, i18n) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm')

  class RedirectConfirmer extends Backbone.View
    className: 'modal hide fade'
    attributes:
      role: 'dialog'

    events:
      'click .cancel-upload': '_onCancelUpload'

    template: _.template('''
      <div class="modal-header">
        <button type="button" class="close" data-dismiss="modal" aria-hidden="true">×</button>
        <h3 id="myModalLabel"><%- t('confirm_cancel.title') %></h3>
      </div>
      <div class="modal-body">
        <p><%- t('confirm_cancel.prompt') %></p>
      </div>
      <div class="modal-footer">
        <button class="btn uncancel" data-dismiss="modal" aria-hidden="true"><%- t('confirm_cancel.back_button') %></button>
        <button class="btn btn-primary cancel-upload"><%- t('confirm_cancel.confirm_button') %></button>
      </div>
    ''')

    initialize: ->
      throw 'Must pass model, a MassUpload' if !@model?
      throw 'Must pass redirectFunctions, a hash of { href: ..., hash: ... } with which each function modifies window.location' if !@options.redirectFunctions? || !@options.redirectFunctions.href? || !@options.redirectFunctions.hash?

      @render()

    render: ->
      html = @template(t: t)
      @$el.html(html)
      @_interceptDocumentLinkClicks()
      @

    _interceptDocumentLinkClicks: ->
      $(document).on 'click.mass-upload-redirect-confirmer', 'a[href]', (e) =>
        if @_shouldIntercept()
          e.preventDefault()
          @tryPromptAndRedirect(href: e.target.getAttribute('href'))
        else
          # do nothing

    remove: ->
      @$el.modal('hide')
      $(document).off('.modal')
      $(document).off('.mass-upload-redirect-confirmer')
      super()

    # Redirects, using @options.redirectFunctions and the previously-stored
    # @_urlSpec.
    _doRedirect: ->
      for k, v of @_urlSpec
        @options.redirectFunctions[k](v)
      this

    _shouldIntercept: ->
      @model.uploads.length != 0

    _showModal: ->
      @$('button').prop('disabled', false)
      @$el.modal('show')
      this

    # Tries to set window.location.[whatever] = [whatever]
    #
    # Call it like this: `tryPromptAndRedirect(href: 'https://example.org')`
    #
    # If an upload is in progress, the confirmer will ask the user to confirm
    # the page change. If the user confirms, the confirmer will abort the
    # transfer and try to delete the upload before redirecting.
    tryPromptAndRedirect: (urlSpec) ->
      @_urlSpec = urlSpec

      if @_shouldIntercept()
        @_showModal()
      else
        @_doRedirect()

    _onCancelUpload: ->
      @model.abort()
      @$('button').attr('disabled', 'disabled')
      $.ajax('/files', type: 'DELETE')
        .done =>
          @$el.modal('hide')
          @_doRedirect()
