define [
  'underscore'
  'backbone'
  'i18n'
  'apps/ImportOptions/app'
  'apps/MassUploadForm/views/UploadProgressView'
], (_, Backbone, i18n, ImportOptionsApp, UploadProgressView) ->
  t = i18n.namespaced('views.DocumentSet._massUploadForm')

  class MassUploadForm extends Backbone.View
    template: _.template('''
      <div class="uploads"></div>

      <div class="controls">
        <fieldset class="upload-buttons">
          <div class="upload-prompt">
            <button class="btn btn-primary select-files" type="button">
              <i class="icon overview-icon-plus"></i>
              <%- t('upload_prompt') %>
            </button>
            <input name="file" type="file" class="invisible-file-input" multiple="multiple" />
          </div>

          <% if (isFolderUploadSupported) { %>
            <div class="upload-folder-prompt">
              <button class="btn btn-primary select-folders" type="button">
                <i class="icon overview-icon-plus"></i>
                <%- t('upload_folder_prompt') %>
              </button>
              <input name="folder" type="file" class="invisible-file-input" multiple webkitdirectory />
            </div>
          <% } %>
        </fieldset>

        <fieldset class="finish-buttons" disabled="disabled">
          <button type="button" class="btn cancel"><%- t('cancel') %></button>

          <button type='button' class="btn btn-primary choose-options">
            <i class="icon icon-play-circle"></i>
            <%- t('choose_options') %>
          </button>
        </fieldset>
      </div>

      <div class='progress-bar'></div>

      <div class="wait-for-import">
        <%- t('wait_for_import') %>
      </div>
      ''')

    events:
      'change .invisible-file-input': '_addFiles'
      'drop .uploads': '_dropFiles'
      'dragover .uploads': '_dragoverFiles'
      'mouseenter .invisible-file-input': '_addButtonHover'
      'mouseleave .invisible-file-input': '_removeButtonHover'
      'click .choose-options': '_requestOptions'
      'click .cancel': '_confirmCancel'

    initialize: (options) ->
      @options = options

      throw 'Must set uploadCollectionViewClass, a Backbone.View' if !@options.uploadCollectionViewClass?
      throw 'Must pass supportedLanguages, an Array of { code: "en", name: "English" } values' if !@options.supportedLanguages?
      throw 'Must pass defaultLanguageCode, a language code like "en"' if !@options.defaultLanguageCode?

      @collection = @model.uploads
      @listenTo(@collection, 'reset', @_onCollectionReset)
      @listenTo(@collection, 'add-batch', @_onCollectionAddBatch)
      @listenTo(@model, 'change', @_maybeSubmit)
      @listenTo(@model, 'change', @_refreshFinishEnabled)
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

      @_refreshFinishEnabled()
      @_refreshProgressVisibility()
      @_progressView = new UploadProgressView(model: @model, el: @_$els.progressBar)
      @_progressView.render()

      @_uploadCollectionView = new @options.uploadCollectionViewClass(
        collection: @collection
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
      @_optionsSetDone(false)
      @_refreshFinishEnabled()

    _onCollectionAddBatch: ->
      @_refreshFinishEnabled()
      @_refreshProgressVisibility()

    _setButtonHover: (event, isHovering) ->
      $file = $(event.currentTarget)
      $parent = $file.parent()
      $button = $parent.children().eq(0)
      $button.toggleClass('hover', isHovering)

    _addButtonHover: (e) -> @_setButtonHover(e, true)
    _removeButtonHover: (e) -> @_setButtonHover(e, false)

    _dragoverFiles: (e) ->
      e = e.originalEvent  # unwrap jQuery
      for item in e.dataTransfer.items
        if item.kind == "file" || item.webkitGetAsEntry?()
          # User wants to drop files! That's okay.
          e.preventDefault()  # prevent "no, can't drop" browser default
          return

    _dropFiles: (e) ->
      e = e.originalEvent  # unwrap jQuery
      e.preventDefault()  # prevent "view file contents" browser default

      toAdd = []
      for item in e.dataTransfer.items
        # if item.webkitGetAsEntry exists, we support directory API
        # if item.getWebkitAsEntry() returns an entry, this is a file or directory
        # if item.getWebkitAsEntry().createReader exists, this is a directory
        entry = item.webkitGetAsEntry?()
        directoryReader = entry?.createReader?()
        if directoryReader
          @_addDirectoryAsync(directoryReader)
        else
          file = item.getAsFile()
          if file
            toAdd.push(file)

      if toAdd.length
        @model.addFiles(toAdd)

    _addEntriesAsync: (entries) ->
      # FileSystemFileEntry API
      #
      # Add files, and add dirs' files in the background with _addDirectoryAsync().
      #
      # Async may cause races ... but this isn't likely to affect most users.
      addFile = (entry, file) =>
        @model.uploads.addWithMerge([{ id: entry.fullPath.slice(1), file: file }])

      entries.forEach (entry) =>
        if entry.createReader  # it's a directory
          @_addDirectoryAsync(entry.createReader())
        else
          entry.file(((f) => addFile(entry, f)), console.error)

    _addDirectoryAsync: (reader) ->
      # FileSystemFileEntry API
      #
      # Add a dir's files in the background. This can cause a race and a user
      #
      # Async may cause races ... but this isn't likely to affect most users.
      onSuccess = (entries) =>
        if entries.length == 0
          return # No more files
        @_addEntriesAsync(entries)
        @_addDirectoryAsync(reader)  # call reader.readEntries() until empty

      reader.readEntries(onSuccess, console.error)

    _requestOptions: (e) ->
      e.stopPropagation()
      e.preventDefault()
      ImportOptionsApp.addHiddenInputsThroughDialog(
        @el,
        onlyOptions: @options.onlyOptions || [ 'name', 'lang', 'split_documents', 'ocr', 'metadata_json' ]
        supportedLanguages: @options.supportedLanguages
        defaultLanguageCode: @options.defaultLanguageCode
        documentSet: @options.documentSet
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

    _refreshFinishEnabled: ->
      enabled = @model.uploads.length > 0
      @$('fieldset.finish-buttons').prop('disabled', !enabled)

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
