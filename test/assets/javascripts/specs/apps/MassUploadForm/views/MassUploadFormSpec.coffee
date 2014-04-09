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
    isFolderUploadSupported = 'webkitdirectory' of document.createElement('input')

    class UploadCollectionView extends Backbone.View

    addSomeFiles = ->
      model.uploads.add(new MockUpload)
      model.uploads.add(new MockUpload)
      model.uploads.add(new MockUpload)
      model.uploads.trigger('add-batch', model.uploads.models)

    class MockUpload extends Backbone.Model
      initialize: ->
        @id = @cid

      isFullyUploaded: ->
        @get('isFullyUploaded')

    mockFileInput = (selector) ->
      # A file input can't have its "files" attribute set. But we need
      # to mock that, so we'll replace it with a div.
      #
      # Returns the mock file input
      $original = view.$(selector)
      $fileInput = $("<div class='invisible-file-input' accept='#{$original.attr('accept')}'></div>")
      $original.replaceWith($fileInput)
      $fileInput

    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @server = sinon.fakeServer.create()

      i18n.reset_messages
        'views.DocumentSet._massUploadForm.upload_prompt': 'upload_prompt'
        'views.DocumentSet._massUploadForm.upload_folder_prompt': 'upload_folder_prompt'
        'views.DocumentSet._massUploadForm.choose_options': 'choose_options'
        'views.DocumentSet._massUploadForm.wait_for_import': 'wait_for_import'
        'views.DocumentSet._massUploadForm.cancel': 'cancel'
        'views.DocumentSet._uploadProgress.uploading': 'uploading'

      @sandbox.spy(UploadCollectionView.prototype, 'render')

      model = new Backbone.Model
      model.uploads = new Backbone.Collection
      model.abort = sinon.spy()

      view = new MassUploadForm
        model: model
        uploadCollectionViewClass: UploadCollectionView
        supportedLanguages: [ {code: "en", name: "English"} ]
        defaultLanguageCode: 'en'
      $.extend model,
        addFiles: sinon.spy()

    afterEach ->
      @server.restore()
      @sandbox.restore()
      view.remove()

    describe 'init', ->
      it 'cancels previous uploads', ->
        # remove this when we add resumable uploads

    describe 'render', ->
      beforeEach ->
        view.render()

      it 'has a file input', ->
        expect(view.$('.upload-prompt input[type=file]').length).to.eq(1)

      it 'only shows pdf files by default', ->
        expect(view.$('.upload-prompt input[type=file]').attr('accept')).to.eq('application/pdf')

      it 'has a folder input iff folder upload is supported', ->
        expect(view.$('.upload-folder-prompt').length).to.eq(isFolderUploadSupported && 1 || 0)

      it 'renders uploadCollectionView', ->
        expect(UploadCollectionView.prototype.render).to.have.been.called

      it 'hides the progress bar when there are no uploads', ->
        expect(view.$('.progress-bar').css('display')).to.eq('none')

      it 'hides the minimum-files text when there are no uploads', ->
        expect(view.$('.minimum-files').css('display')).to.eq('none')

    describe 'model add event', ->
      beforeEach ->
        view.render()
        model.uploads.add(new MockUpload)
        model.uploads.trigger('add-batch', model.uploads.models)

      it 'does not yet enable the submit button', ->
        expect(view.$('.choose-options')).to.be.disabled

      it 'shows the progress bar', ->
        expect(view.$('.progress-bar').css('display')).to.eq('block')

      it 'shows the minimum-files text', ->
        expect(view.$('.minimum-files').css('display')).not.to.eq('none')

      describe 'with 3 or more uploads', ->
        beforeEach ->
          model.uploads.add(new MockUpload)
          model.uploads.add(new MockUpload)
          model.uploads.trigger('add-batch', model.uploads.tail(1))

        it 'hides the minimum-files text', ->
          expect(view.$('.minimum-files').css('display')).to.eq('none')

        describe 'submit button', ->
          it 'is enabled', ->
            expect(view.$('.choose-options')).not.to.be.disabled

          it 'shows a modal with the import options app', ->
            @sandbox.stub(ImportOptionsApp, 'addHiddenInputsThroughDialog')
            view.$('.choose-options').click()
            expect(ImportOptionsApp.addHiddenInputsThroughDialog).to.have.been.calledWith(
              sinon.match.has('childNodes'),
              onlyOptions: [ 'name', 'lang', 'split_documents', 'supplied_stop_words', 'important_words' ]
              supportedLanguages: sinon.match.array
              defaultLanguageCode: 'en'
              callback: sinon.match.func
            )

          describe 'after selecting options', ->
            it 'disables the "set options" button', ->
              @sandbox.stub(ImportOptionsApp, 'addHiddenInputsThroughDialog', (el, options) -> options.callback())
              view.$('.choose-options').click()
              expect(view.$('button.choose-options')).to.be.disabled
              expect(view.$('.upload-prompt button.select-files')).to.be.disabled
              expect(view.$('.upload-prompt :file')).to.be.disabled
              if isFolderUploadSupported
                expect(view.$('.upload-folder-prompt button.select-folders')).to.be.disabled
                expect(view.$('.upload-folder-prompt :file')).to.be.disabled

    describe 'dom events', ->
      it 'changes the button hover state when the invisible input is hovered', ->
        view.render()
        $input = view.$('.upload-prompt :file')
        $button = view.$('.upload-prompt button')
        $input.trigger('mouseenter')
        expect($button).to.have.class('hover')
        $input.trigger('mouseleave')
        expect($button).not.to.have.class('hover')

      if isFolderUploadSupported
        it 'changes the upload-folder button hover state when the invisible input is hovered', ->
          view.render()
          $input = view.$('.upload-folder-prompt :file')
          $button = view.$('.upload-folder-prompt button')
          $input.trigger('mouseenter')
          expect($button).to.have.class('hover')
          $input.trigger('mouseleave')
          expect($button).not.to.have.class('hover')

    describe 'uploading files', ->
      fileList = undefined
      $fileInput = undefined

      beforeEach ->
        view.render()

        fileList = [ { type: 'application/pdf' }, { type: 'application/pdf' } ]  # two things
        $fileInput = mockFileInput('.upload-prompt :file')
        $fileInput[0].files = fileList
        $fileInput.trigger('change')

      it 'queues files for uploading', ->
        expect(model.addFiles).to.have.been.calledWith(fileList)

      it 'clears the file input once files have been queued', ->
        expect($fileInput[0].value).to.eq('')

    if isFolderUploadSupported
      describe 'uploading folders', ->
        fileList = undefined
        $fileInput = undefined

        beforeEach ->
          view.render()

          fileList = [
            { name: '.', type: '', webkitRelativePath: 'x/.' }
            { name: 'a.pdf', type: 'application/pdf', webkitRelativePath: 'x/foo/a.pdf' }
            { name: 'b.exe', type: 'application/octet-stream', webkitRelativePath: 'x/foo/b.exe' }
            { name: 'c.pdf.t', type: 'application/pdf', webkitRelativePath: 'x/bar/c.pdf.t' }
          ]
          $fileInput = mockFileInput('.upload-folder-prompt :file')
          $fileInput[0].files = fileList
          $fileInput.trigger('change')

        it 'queues files for uploading', ->
          expect(model.addFiles).to.have.been.calledWith([
            { name: 'a.pdf', type: 'application/pdf', webkitRelativePath: 'x/foo/a.pdf' }
            { name: 'c.pdf.t', type: 'application/pdf', webkitRelativePath: 'x/bar/c.pdf.t' }
          ])

        it 'clears the file input once files have been queued', ->
          expect($fileInput[0].value).to.eq('')

    describe 'buttons', ->
      beforeEach ->
        view.render()

      it 'has an add files button', ->
        expect(view.$el.text()).to.match(/upload_prompt/)

      describe 'choose options button', ->
        it 'has a "finished selecting files" button', ->
          expect(view.$('.choose-options').length).to.eq(1)
          expect(view.$el.text()).to.match(/choose_options/)

        it 'is disabled with no files selected', ->
          expect(view.$('.choose-options')).to.be.disabled

        it 'is disabled after reset', ->
          addSomeFiles()
          model.uploads.reset([])

          expect(view.$('button.choose-options')).to.be.disabled

        it 'is enabled after reset and then selecting options', ->
          addSomeFiles()
          model.uploads.reset([])
          addSomeFiles()

          expect(view.$('button.choose-options')).not.to.be.disabled

        describe 'after selecting options', ->
          beforeEach ->
            # add some finished uploads
            addSomeFiles()
            model.set(status: 'waiting')

            @sandbox.stub(ImportOptionsApp, 'addHiddenInputsThroughDialog', (el, options) -> options.callback())
            view.$('.choose-options').click()
            $('body').append(view.el)

          it 'disables itself and the select files button', ->
            expect(view.$('button.choose-options')).to.be.disabled
            expect(view.$('button.select-files')).to.be.disabled
            expect(view.$('.upload-prompt :file')).to.be.disabled

          it 'shows the finished importing text', ->
            expect(view.$('.wait-for-import')).to.be.visible

          it 'hides the finished importing text on cancel', ->
            model.uploads.reset()
            expect(view.$('.wait-for-import')).not.to.be.visible

      describe 'cancel button', ->
        it 'has a cancel button with the correct message', ->
          expect(view.$('.cancel').length).to.eq(1)
          expect(view.$el.text()).to.match(/cancel/)

        it 'triggers "cancel"', ->
          spy = sinon.spy()
          view.on('cancel', spy)
          view.$('.cancel').click()
          expect(spy).to.have.been.called

    describe 'finishing upload', ->
      beforeEach ->
        @sandbox.stub($.fn, 'submit')
        view.render()

        model.uploads.add(new MockUpload)
        model.uploads.add(new MockUpload)
        model.uploads.add(new MockUpload)
        model.uploads.trigger('add-batch', model.uploads.models)

      it 'submits the form when uploading is finished and options are chosen', ->
        # finish the uploads
        model.set(status: 'waiting')

        # choose options
        @sandbox.stub(ImportOptionsApp, 'addHiddenInputsThroughDialog', (el, options) -> options.callback())
        view.$('.choose-options').click()

        expect($.fn.submit).to.have.been.called

      it 'submits the form when options are set before the upload is done', ->
        # uploads are in progress
        model.set(status: 'uploading')

        # choose options
        @sandbox.stub(ImportOptionsApp, 'addHiddenInputsThroughDialog', (el, options) -> options.callback())
        view.$('.choose-options').click()

        # finish uploading
        model.set(status: 'waiting')

        expect($.fn.submit).to.have.been.called

      it 'does not submit the form until the upload is finished', ->
        @sandbox.stub(ImportOptionsApp, 'addHiddenInputsThroughDialog', (el, options) -> options.callback())
        view.$('.choose-options').click()

        expect($.fn.submit).not.to.have.been.called

      it 'hides the progress bar when the upload finishes', ->
        # finish uploads
        model.set(status: 'waiting')

        expect(view.$el.find('.progress-bar').css('display')).to.eq('none')

      it 'shows the progress bar when adding another file', ->
        # finish uploads
        model.set(status: 'waiting')

        # now, add an unfinished upload
        model.uploads.add(new MockUpload)
        model.uploads.trigger('add-batch', model.uploads.last(1))
        model.set(status: 'uploading')

        expect(view.$el.find('.progress-bar').css('display')).to.eq('block')
