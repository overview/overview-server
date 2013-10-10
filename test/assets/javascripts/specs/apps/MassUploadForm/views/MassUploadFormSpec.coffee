define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/MassUploadForm'
  'i18n'
  'apps/ImportOptions/app'
], ($, Backbone, MassUploadForm, i18n, ImportOptionsApp) ->
  describe 'apps/MassUploadForm/views/MassUploadForm', ->
    model = undefined
    view = undefined
    uploadViewRenderSpy = undefined

    mockFileInput = ->
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
        'views.DocumentSet._massUploadForm.choose_options': 'choose_options'

      uploadViewClass = Backbone.View.extend(tagName: 'li')
      uploadViewRenderSpy = spyOn(uploadViewClass.prototype, 'render').andCallThrough()
      model = new Backbone.Model
      model.uploads = new Backbone.Collection
      view = new MassUploadForm
        model: model
        uploadViewClass: uploadViewClass
        supportedLanguages: [ {code: "en", name: "English"} ]
        defaultLanguageCode: 'en'
      $.extend model,
        addFiles: jasmine.createSpy()

    describe 'render', ->
      it 'has a file input', ->
        view.render()
        expect(view.$el.find('input[type=file]').length).toEqual(1)

      it 'only shows pdf files by default', ->
        view.render()
        expect(view.$el.find('input[type=file]').attr('accept')).toEqual('application/pdf')

    describe 'model add event', ->
      beforeEach ->
        view.render()
        model.uploads.add(new Backbone.Model())
        waits(0) # We defer the add, as it seems more responsive that way

      it 'adds an uploadView when a file is added', ->
        runs ->
          expect(uploadViewRenderSpy).toHaveBeenCalled()
          expect(view.$el.find('.files li').length).toEqual(1)

      describe 'submit button', ->
        it 'enables the submit button', ->
          expect(view.$('.choose-options')).not.toBeDisabled()

        it 'shows a modal with the import options app', ->
          spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog')
          view.$('.choose-options').click()
          expect(ImportOptionsApp.addHiddenInputsThroughDialog).toHaveBeenCalledWith(
            jasmine.any(HTMLElement),
            supportedLanguages: jasmine.any(Array)
            defaultLanguageCode: 'en'
            excludeOptions: ['split_documents']
            callback: jasmine.any(Function)
          )

        describe 'after selecting options', ->
          it 'disables the "set options" button', ->
            spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
            view.$('.choose-options').click()
            expect(view.$('button.choose-options')).toBeDisabled()
            expect(view.$('button.select-files')).toBeDisabled()
            expect(view.$(':file')).toBeDisabled()


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
        $fileInput = mockFileInput()
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

      describe 'choose options button', ->
        it 'has a "finished selecting files" button', ->
          view.render()
          expect(view.$('.choose-options').length).toEqual(1)
          expect(view.$el.text()).toMatch(/choose_options/)

        it 'is disabled with no files selected', ->
          view.render()
          expect(view.$('.choose-options')).toBeDisabled()

    describe 'form submission', ->
      submitSpy = undefined

      beforeEach ->
        submitSpy = spyOn($.fn, 'submit')
        view.render()

      it 'submits the form when uploading is finished and options are chosen', ->
        # add a finished upload
        model.uploads.add(new Backbone.Model)
        model.set(status: 'waiting')

        # choose options
        spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
        view.$('.choose-options').click()

        expect(submitSpy).toHaveBeenCalled()

      it 'submits the form when options are set before the upload is done', ->
        # add an upload
        model.uploads.add(new Backbone.Model)

        # choose options
        spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
        view.$('.choose-options').click()

        # finish uploading
        model.set(status: 'waiting')

        expect(submitSpy).toHaveBeenCalled()

      it 'does not submit the form until the upload is finished', ->
        model.uploads.add(new Backbone.Model)
        spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
        view.$('.choose-options').click()

        expect(submitSpy).not.toHaveBeenCalled()
