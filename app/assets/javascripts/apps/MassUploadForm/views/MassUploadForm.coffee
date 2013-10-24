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
        <ul class='files'>
          <li class="empty-upload">
            <i class='icon-cloud-upload'></i>
            <div><%- t('drop_target') %></div>
          </li>
        </ul>

        <div class='controls'>
          <button type='button' class='cancel btn'>
            <%- t('cancel') %>
          </button>

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
      'click .cancel': '_cancel'

    initialize: ->
      throw 'Must set uploadViewClass, a Backbone.View' if !@options.uploadViewClass?
      throw 'Must pass supportedLanguages, an Array of { code: "en", name: "English" } values' if !@options.supportedLanguages?
      throw 'Must pass defaultLanguageCode, a language code like "en"' if !@options.defaultLanguageCode?

      @collection = @model.uploads
      @listenTo(@collection, 'add', (model) => @_onCollectionAdd(model))
      @uploadViewClass = @options.uploadViewClass
      @finishEnabled = false
      @listenTo(@model, 'change', @_shouldSubmit)
      @optionsSet = false

      $('div.nav-buttons a.back').click =>
        @_cancel()

      $('div.nav-buttons li a').click =>
        @_cancel()

    render: ->
      @$el.html(@template(t: t))
      @$ul = @$el.find('.files')

    setHash: (hash) ->
      window.location.hash = hash  #for testability

    _addFiles: ->
      fileInput = @$el.find('.invisible-file-input')[0]
      @model.addFiles(fileInput.files)
      fileInput.value = ''

    _onCollectionAdd: (model) ->
      uploadView = new @uploadViewClass(model: model)
      uploadView.render()

      if ! @finishEnabled
        @$('button.choose-options').prop('disabled', false)
        @finishEnabled = true

      @$ul.find('.empty-upload').remove()

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
        excludeOptions: ['split_documents']
        callback: => @_optionsSetDone()
      )

    _optionsSetDone: ->
      @$('button.choose-options, button.select-files, :file').prop('disabled', true)
      @$('.wait-for-import').css('display', 'block')
      @optionsSet = true
      @_shouldSubmit()

    _shouldSubmit: ->
      if(
        @model.uploads.length > 0 &&
        @model.get('status') == 'waiting' &&
        @optionsSet
      )
        @$el.closest('form').submit()

    _cancel: ->
      @model.uploads.each (upload) =>
        @model.removeUpload(upload)

      $.ajax('/files', type: 'DELETE')
      @model.uploads.reset()
      @render()
      @finishEnabled = false
      @setHash('')
