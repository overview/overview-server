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
    uploadCollectionViewRenderSpy = undefined

    class MockUpload extends Backbone.Model
      initialize: ->
        @id = @cid

      isFullyUploaded: ->
        @get('isFullyUploaded')

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
        'views.DocumentSet._massUploadForm.wait_for_import': 'wait_for_import'
        'views.DocumentSet._massUploadForm.cancel': 'cancel'
        'views.DocumentSet._uploadProgress.uploading': 'uploading'

      clearAjaxRequests()

      uploadCollectionViewClass = Backbone.View
      uploadCollectionViewRenderSpy = spyOn(uploadCollectionViewClass.prototype, 'render').andCallThrough()

      model = new Backbone.Model
      model.uploads = new Backbone.Collection
      model.abort = jasmine.createSpy()

      # fake the import options menu
      $('body').append('<div class="nav-buttons"><ul><li><a href="#foo">foo</a></li></ul></div>')

      view = new MassUploadForm
        model: model
        uploadCollectionViewClass: uploadCollectionViewClass
        supportedLanguages: [ {code: "en", name: "English"} ]
        defaultLanguageCode: 'en'
      $.extend model,
        addFiles: jasmine.createSpy()

    afterEach ->
      view.remove()
      $('div.nav-buttons').remove()

    describe 'init', ->
      it 'cancels previous uploads', ->
        # remove this when we add resumable uploads
        # expect(mostRecentAjaxRequest().url).toEqual('/files')
        # expect(mostRecentAjaxRequest().method).toEqual('DELETE')

    describe 'render', ->
      beforeEach ->
        view.render()

      it 'has a file input', ->
        expect(view.$('input[type=file]').length).toEqual(1)

      it 'only shows pdf files by default', ->
        expect(view.$('input[type=file]').attr('accept')).toEqual('application/pdf')

      it 'renders uploadCollectionView', ->
        expect(uploadCollectionViewRenderSpy).toHaveBeenCalled()

      it 'hides the progress bar when there are no uploads', ->
        expect(view.$('.progress-bar').css('display')).toEqual('none')

    describe 'model add event', ->
      beforeEach ->
        view.render()
        model.uploads.add(new MockUpload)
        model.uploads.trigger('add-batch', model.uploads.models)

      it 'does not yet enable the submit button', ->
        expect(view.$('.choose-options')).toBeDisabled()

      it 'shows the progress bar', ->
        expect(view.$('.progress-bar').css('display')).toEqual('block')

      describe 'with 3 or more uploads', ->
        beforeEach ->
          model.uploads.add(new MockUpload)
          model.uploads.add(new MockUpload)
          model.uploads.trigger('add-batch', model.uploads.tail(1))

        describe 'submit button', ->
          it 'is enabled', ->
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
      beforeEach ->
        view.render()

      it 'has an add files button', ->
        expect(view.$el.text()).toMatch(/upload_prompt/)

      describe 'choose options button', ->
        it 'has a "finished selecting files" button', ->
          expect(view.$('.choose-options').length).toEqual(1)
          expect(view.$el.text()).toMatch(/choose_options/)

        it 'is disabled with no files selected', ->
          expect(view.$('.choose-options')).toBeDisabled()

        describe 'after selecting options', ->
          beforeEach ->
            # add some finished uploads
            model.uploads.add(new MockUpload)
            model.uploads.add(new MockUpload)
            model.uploads.add(new MockUpload)
            model.uploads.trigger('add-batch', model.uploads.models)
            model.set(status: 'waiting')

            spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
            view.$('.choose-options').click()

          it 'disables itself and the select files button', ->
            expect(view.$('button.choose-options')).toBeDisabled()
            expect(view.$('button.select-files')).toBeDisabled()
            expect(view.$(':file')).toBeDisabled()

          it 'shows the finished importing text', ->
            expect(view.$('.wait-for-import')).toHaveCss(display: 'block')

      describe 'cancel button', ->
        it 'has a cancel button with the correct message', ->
          expect(view.$('.cancel').length).toEqual(1)
          expect(view.$el.text()).toMatch(/cancel/)

        it 'triggers "cancel"', ->
          spy = jasmine.createSpy('cancel callback')
          view.on('cancel', spy)
          view.$('.cancel').click()
          expect(spy).toHaveBeenCalled()

    describe 'finishing upload', ->
      submitSpy = undefined

      beforeEach ->
        submitSpy = spyOn($.fn, 'submit')
        view.render()

        model.uploads.add(new MockUpload)
        model.uploads.add(new MockUpload)
        model.uploads.add(new MockUpload)
        model.uploads.trigger('add-batch', model.uploads.models)

      it 'submits the form when uploading is finished and options are chosen', ->
        # finish the uploads
        model.set(status: 'waiting')

        # choose options
        spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
        view.$('.choose-options').click()

        expect(submitSpy).toHaveBeenCalled()

      it 'submits the form when options are set before the upload is done', ->
        # uploads are in progress
        model.set(status: 'uploading')

        # choose options
        spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
        view.$('.choose-options').click()

        # finish uploading
        model.set(status: 'waiting')

        expect(submitSpy).toHaveBeenCalled()

      it 'does not submit the form until the upload is finished', ->
        spyOn(ImportOptionsApp, 'addHiddenInputsThroughDialog').andCallFake( (el, options) -> options.callback() )
        view.$('.choose-options').click()

        expect(submitSpy).not.toHaveBeenCalled()

      it 'hides the progress bar when the upload finishes', ->
        # finish uploads
        model.set(status: 'waiting')

        expect(view.$el.find('.progress-bar').css('display')).toEqual('none')

      it 'shows the progress bar when adding another file', ->
        # finish uploads
        model.set(status: 'waiting')

        # now, add an unfinished upload
        model.uploads.add(new MockUpload)
        model.uploads.trigger('add-batch', model.uploads.last(1))
        model.set(status: 'uploading')

        expect(view.$el.find('.progress-bar').css('display')).toEqual('block')
