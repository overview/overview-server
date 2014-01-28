define [
  'underscore'
  'backbone'
  'i18n'
  'apps/ImportOptions/app'
  'apps/MassUploadForm/views/UploadView'
  'apps/MassUploadForm/views/UploadProgressView'
], (_, Backbone, i18n, ImportOptionsApp, UploadView, UploadProgressView) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm')

  class MassUploadForm extends Backbone.View
    template: _.template('''
      <div>
        <div class='uploads'></div>

        <div class='progress-bar'></div>

        <div class='controls'>
          <a href="#" class="btn cancel"><%- t('cancel') %></a>

          <div class='right-controls'>
            <div class="upload-prompt">
              <button class="btn btn-primary select-files" type="button">
                <i class="overview-icon-plus"></i>
                <%- t('upload_prompt') %>
              </button>
              <input type="file" class="invisible-file-input" accept="application/pdf" multiple="multiple" />
            </div>

            <button type='button' class="btn btn-primary choose-options" disabled="disabled">
              <i class="icon-play-circle"></i>
              <%- t('choose_options') %>
            </button>
          </div>
        </div>
      </div>

      <div class="wait-for-import">
        <%- t('wait_for_import') %>
      </div>

      <div id="cancel-modal" class="modal hide fade" role="dialog">
        <div class="modal-header">
          <button type="button" class="close" data-dismiss="modal" aria-hidden="true">×</button>
          <h3 id="myModalLabel"><%- t('confirm_cancel.title') %></h3>
        </div>
        <div class="modal-body">
          <p><%- t('confirm_cancel.prompt') %></p>
        </div>
        <div class="modal-footer">
          <button class="btn" data-dismiss="modal" aria-hidden="true"><%- t('confirm_cancel.back_button') %></button>
          <button class="btn btn-primary cancel-upload"><%- t('confirm_cancel.confirm_button') %></button>
        </div>
      </div>
      ''')

    events:
      'change .invisible-file-input': '_addFiles'
      'mouseenter .invisible-file-input': '_addButtonHover'
      'mouseleave .invisible-file-input': '_removeButtonHover'
      'click .choose-options': '_requestOptions'
      'click .cancel': '_confirmCancel'

    initialize: ->
      throw 'Must set uploadCollectionViewClass, a Backbone.View' if !@options.uploadCollectionViewClass?
      throw 'Must pass supportedLanguages, an Array of { code: "en", name: "English" } values' if !@options.supportedLanguages?
      throw 'Must pass defaultLanguageCode, a language code like "en"' if !@options.defaultLanguageCode?

      @collection = @model.uploads
      @listenTo(@collection, 'add-batch', @_onCollectionAddBatch)
      @finishEnabled = false
      @listenTo(@model, 'change', @_shouldSubmit)
      @listenTo(@model, 'change', @_refreshProgressVisibility)
      @optionsSet = false

      # remove this when we add resumable uploads
      #$.ajax('/files', type: 'DELETE')

      $('div.nav-buttons a.back').click (e) =>
        if(@getHash() == '#import-from-mass-upload')
          @_confirmCancel(e)

      $('div.nav-buttons li a').click (e) =>
        if(@getHash() == '#import-from-mass-upload')
          @_confirmCancel(e)

      $('nav a').click (e) =>
        @_confirmNav(e)

    render: ->
      @$el.html(@template(t: t))
      @$ul = @$el.find('.files')
      @$ulEmptyUpload = @$ul.find('.empty-upload')
      @$progressBar = @$('.progress-bar')

      @_refreshProgressVisibility()
      @_progressView = new UploadProgressView(model: @model, el: @$progressBar)
      @_progressView.render()

      @_uploadCollectionView = new @options.uploadCollectionViewClass(
        collection: @collection,
        uploadViewClass: UploadView
        el: @$('.uploads')
      )
      @_uploadCollectionView.render()

    remove: ->
      @_progressView?.remove()
      @_uploadCollectionView?.remove()
      super()

    setHash: (hash) ->
      window.location.hash = hash  #for testability

    getHash: ->
      window.location.hash #for testability

    _addFiles: ->
      fileInput = @$el.find('.invisible-file-input')[0]
      @model.addFiles(fileInput.files)
      fileInput.value = ''

    _onCollectionAddBatch: ->
      if ! @finishEnabled && @collection.length > 2
        @$('button.choose-options').prop('disabled', false)
        @finishEnabled = true

      @_refreshProgressVisibility()

    _addButtonHover: ->
      @$el.find('button.select-files').addClass('hover')

    _removeButtonHover: ->
      @$el.find('button.select-files').removeClass('hover')

    _requestOptions: (e) ->
      e.stopPropagation()
      e.preventDefault()
      ImportOptionsApp.addHiddenInputsThroughDialog(
        @el,
        supportedLanguages: @options.supportedLanguages
        defaultLanguageCode: @options.defaultLanguageCode
        excludeOptions: ['split_documents']
        callback: => @_optionsSetDone()
      )

    _optionsSetDone: ->
      @$('button.choose-options, button.select-files, :file').prop('disabled', true)
      @$('.wait-for-import').css('display', 'block')
      @optionsSet = true
      @_shouldSubmit()

    _shouldSubmit: ->
      if @_uploadDone() && @optionsSet
        @$el.closest('form').submit()

    _refreshProgressVisibility: ->
      @_progressIsVisible ?= true
      newIsVisible = @model.uploads.length && !@_uploadDone()

      if newIsVisible != @_progressIsVisible
        @_progressIsVisible = newIsVisible
        cssDisplay = if @_progressIsVisible then 'block' else 'none'
        @$progressBar.css('display', cssDisplay)

    _uploadDone: ->
      @model.uploads.length > 0 && @model.get('status') == 'waiting'

    _confirmNav: (e) ->
      if @collection.length > 0 && e.currentTarget.target != '_blank'
        e.preventDefault()
        e.stopPropagation()
        targetUrl = e.currentTarget.href
        @$el.find('#cancel-modal').modal('show')
        @$el.find('#cancel-modal .cancel-upload').click =>
          window.location = targetUrl
          @_cancel()

    _confirmCancel: (e) ->
      e.stopPropagation()
      e.preventDefault()

      target = e.currentTarget.hash

      if @collection.length > 0
        @$el.find('#cancel-modal').modal('show')
        @$el.find('#cancel-modal .cancel-upload').click =>
          @_cancel(target)
      else
        @_cancel(target)

    _cancel: (target) ->
      @$el.find('#cancel-modal').modal('hide')
      @model.abort()
      $.ajax('/files', type: 'DELETE').done =>
        @render()
        @finishEnabled = false
        @setHash(target || '')
