define [
  'underscore'
  'backbone'
  'i18n'
  'apps/ImportOptions/app'
], (_, Backbone, i18n, ImportOptionsApp) ->
  t = (m, args...) -> i18n("views.DocumentSet._massUploadForm.#{m}", args...)

  Backbone.View.extend
    template: _.template('''
      <div>
        <div class="upload-prompt">
          <button class="btn btn-primary select-files" type="button">
            <i class="overview-icon-plus"></i>
            <%- t('upload_prompt') %>
          </button>
          <input type="file" class="invisible-file-input" multiple="multiple" />
        </div>

        <button type='button' class="upload-submit btn" disabled="disabled">
          <i class="icon-play-circle"></i>
          <%- t('submit') %>
        </button>
      </div>
      <ul class='files'></ul>
      ''')

    events:
      'change .invisible-file-input': '_addFiles'
      'mouseenter .invisible-file-input': '_addButtonHover'
      'mouseleave .invisible-file-input': '_removeButtonHover'
      'click .upload-submit': '_requestOptions'

    initialize: ->
      throw 'Must set uploadViewClass, a Backbone.View' if !@options.uploadViewClass?
      throw 'Must pass supportedLanguages, an Array of { code: "en", name: "English" } values' if !@options.supportedLanguages?
      throw 'Must pass defaultLanguageCode, a language code like "en"' if !@options.defaultLanguageCode?

      @collection = @model.uploads
      @listenTo(@collection, 'add', (model) => @_onCollectionAdd(model))
      @uploadViewClass = @options.uploadViewClass
      @finishEnabled = false

    render: ->
      @$el.html(@template(t: t))
      @$ul = @$el.find('.files')

    _addFiles: ->
      fileInput = @$el.find('.invisible-file-input')[0]
      @model.addFiles(fileInput.files)
      fileInput.value = ''

    _onCollectionAdd: (model) ->
      uploadView = new @uploadViewClass(model: model)
      uploadView.render()

      if ! @finishEnabled
        @$('button.upload-submit').prop('disabled', false)
        @finishEnabled = true

      _.defer => # it seems more responsive when we defer this
        @$ul.append(uploadView.el)

    _addButtonHover: ->
      @$el.find('button.select-files').addClass('hover')

    _removeButtonHover: ->
      @$el.find('button.select-files').removeClass('hover')

    _requestOptions: ->
      ImportOptionsApp.addHiddenInputsThroughDialog(
        @el,
        supportedLanguages: @options.supportedLanguages
        defaultLanguageCode: @options.defaultLanguageCode
        callback: => @_optionsSetDone()
      )

    _optionsSetDone: ->
      @$('button, :file').prop('disabled', true)

