define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/MassUploadForm'
  'i18n'
], ($, Backbone, MassUploadForm, i18n) ->
  describe 'apps/MassUploadForm/views/MassUploadForm', ->
    model = undefined
    view = undefined
    uploadViewRenderSpy = undefined

    mockOutFileInput = ->
      # A file input can't have its "files" attribute set. But we need
      # to mock that, so we'll replace it with a div.
      #
      # Returns the mock file input
      $fileInput = $('<div class="invisible-file-input"></div>')
      view.$el.find('.invisible-file-input').replaceWith($fileInput)
      $fileInput

    beforeEach ->
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.upload_prompt': 'upload_prompt'
        'views.DocumentSet._massUploadForm.submit': 'submit'

      uploadViewClass = Backbone.View.extend(tagName: 'li')
      uploadViewRenderSpy = spyOn(uploadViewClass.prototype, 'render').andCallThrough()
      model = new Backbone.Model
      model.uploads = new Backbone.Collection
      view = new MassUploadForm
        model: model
        uploadViewClass: uploadViewClass
      $.extend model,
        addFiles: jasmine.createSpy()

    describe 'render', ->
      it 'has a file input', ->
        view.render()
        expect(view.$el.find('input[type=file]').length).toEqual(1)

    describe 'model events', ->
      it 'adds an uploadView when a file is added', ->
        view.render()
        model.uploads.add(new Backbone.Model())
        expect(uploadViewRenderSpy).toHaveBeenCalled()
        expect(view.$el.find('.files li').length).toEqual(1)

    describe 'dom events', ->
      it 'changes the button hover state when the invisible input is hovered', ->
        view.render()
        view.$(':file').trigger('mouseenter')
        expect(view.$('button')).toHaveClass('hover')
        view.$(':file').trigger('mouseleave')
        expect(view.$('button')).not.toHaveClass('hover')

    describe 'uploading', ->
      fileList = undefined
      $fileInput = undefined

      beforeEach ->
        view.render()

        fileList = [ {}, {} ]  # two things
        $fileInput = mockOutFileInput()
        $fileInput[0].files = fileList
        $fileInput.trigger('change')

      it 'queues files for uploading', ->
        expect(model.addFiles).toHaveBeenCalledWith(fileList)

      it 'clears the file input once files have been queued', ->
        expect($fileInput[0].value).toEqual('')

    describe 'buttons', ->
      it 'has an add files button', ->
        view.render()
        expect(view.$el.text()).toMatch(/upload_prompt/)

      it 'has a submit button', ->
        view.render()
        expect(view.$(':submit').length).toEqual(1)
        expect(view.$el.text()).toMatch(/submit/)


