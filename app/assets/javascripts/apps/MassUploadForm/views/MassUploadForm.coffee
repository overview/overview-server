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

    _confirmCancel: ->
      @trigger('cancel')
