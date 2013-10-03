define [
  'underscore'
  'backbone'
  'i18n'
], (_, Backbone, i18n) ->
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

        <button type='submit' class="upload-submit btn">
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

    initialize: ->
      throw 'Must set uploadViewClass, a Backbone.View' if !@options.uploadViewClass?

      @collection = @model.uploads
      @listenTo(@collection, 'add', (model) => @_onCollectionAdd(model))
      @uploadViewClass = @options.uploadViewClass

    render: ->
      @$el.html(@template(t: t))

    _addFiles: ->
      fileInput = @$el.find('.invisible-file-input')[0]
      @model.addFiles(fileInput.files)
      fileInput.value = ''

    _onCollectionAdd: (model) ->
      uploadView = new @uploadViewClass(model: model)
      uploadView.render()
      @$el.find('.files').append(uploadView.el)

    _addButtonHover: ->
      @$el.find('button.select-files').addClass('hover')

    _removeButtonHover: ->
      @$el.find('button.select-files').removeClass('hover')
