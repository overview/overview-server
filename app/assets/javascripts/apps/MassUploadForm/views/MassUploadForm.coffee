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

        <div class='controls'>
          <a href="#" class="btn cancel"><%- t('cancel') %></a>

          <div class='right-controls'>
            <div class="upload-prompt">
              <button class="btn btn-primary select-files" type="button">
                <i class="icon overview-icon-plus"></i>
                <%- t('upload_prompt') %>
              </button>
              <input type="file" class="invisible-file-input" multiple="multiple" />
            </div>

            <% if (isFolderUploadSupported) { %>
              <div class="upload-folder-prompt">
                <button class="btn btn-primary select-folders" type="button">
                  <i class="icon overview-icon-plus"></i>
                  <%- t('upload_folder_prompt') %>
                </button>
                <input type="file" class="invisible-file-input" multiple webkitdirectory />
              </div>
            <% } %>

            <button type='button' class="btn btn-primary choose-options" disabled="disabled">
              <i class="icon icon-play-circle"></i>
              <%- t('choose_options') %>
            </button>
          </div>
        </div>

        <div class='progress-bar'></div>

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
      @listenTo(@collection, 'reset', @_onCollectionReset)
      @listenTo(@collection, 'add-batch', @_onCollectionAddBatch)
      @finishEnabled = false
      @listenTo(@model, 'change', @_maybeSubmit)
      @listenTo(@model, 'change', @_refreshProgressVisibility)
      @optionsSet = false

      # remove this when we add resumable uploads
      #$.ajax('/files', type: 'DELETE')

    render: ->
      isFolderUploadSupported = 'webkitdirectory' of document.createElement('input')

      @$el.html(@template(t: t, isFolderUploadSupported: isFolderUploadSupported))

      @_$els =
        uploads: @$('.uploads')
        progressBar: @$('.progress-bar')


      @_refreshProgressVisibility()
      @_progressView = new UploadProgressView(model: @model, el: @_$els.progressBar)
      @_progressView.render()

      @_uploadCollectionView = new @options.uploadCollectionViewClass(
        collection: @collection,
        uploadViewClass: UploadView
        el: @_$els.uploads
      )
      @_uploadCollectionView.render()


    remove: ->
      @_progressView?.remove()
      @_uploadCollectionView?.remove()
      super()

    _addFiles: (e) ->
      input = e.currentTarget

      files = input.files

      if input.webkitdirectory
        # remove hidden files
        files = (f for f in files when f.name.charAt(0) != '.')

      @model.addFiles(files)

      input.value = '' # so the user can select files again

    _onCollectionReset: ->
      @finishEnabled = false
      @_optionsSetDone(false)
      @$('button.choose-options').prop('disabled', true)

    _onCollectionAddBatch: ->
      if !@finishEnabled && @collection.length > 0
        @finishEnabled = true
        @$('button.choose-options').prop('disabled', false)

      @_refreshProgressVisibility()

    _setButtonHover: (event, isHovering) ->
      $file = $(event.currentTarget)
      $parent = $file.parent()
      $button = $parent.children().eq(0)
      $button.toggleClass('hover', isHovering)

    _addButtonHover: (e) -> @_setButtonHover(e, true)
    _removeButtonHover: (e) -> @_setButtonHover(e, false)

    _requestOptions: (e) ->
      e.stopPropagation()
      e.preventDefault()
      tooFewDocuments = @collection.length <= 2
      ImportOptionsApp.addHiddenInputsThroughDialog(
        @el,
        onlyOptions: @options.onlyOptions || [ 'name', 'lang', 'split_documents', 'supplied_stop_words', 'important_words' ]
        supportedLanguages: @options.supportedLanguages
        defaultLanguageCode: @options.defaultLanguageCode
        tooFewDocuments: tooFewDocuments
        callback: => @_optionsSetDone(true)
      )

    _optionsSetDone: (toggle) ->
      @$('button.choose-options, button.select-files, button.select-folders, :file').prop('disabled', toggle)
      @$('.wait-for-import').toggle(toggle)
      @optionsSet = toggle
      @_maybeSubmit()

    _maybeSubmit: ->
      if @optionsSet && @_uploadDone()
        @$el.closest('form').submit()

    _refreshProgressVisibility: ->
      @_progressIsVisible ?= true
      newIsVisible = @model.uploads.length && !@_uploadDone()

      if newIsVisible != @_progressIsVisible
        @_progressIsVisible = newIsVisible
        cssDisplay = if @_progressIsVisible then 'block' else 'none'
        @_$els.progressBar.css('display', cssDisplay)

    _uploadDone: ->
      @model.uploads.length > 0 && @model.get('status') == 'waiting'

    _confirmCancel: ->
      @trigger('cancel')
