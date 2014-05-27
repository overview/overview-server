define [
  'jquery'
  'backbone'
  'apps/MassUploadForm/views/RedirectConfirmer'
  'i18n'
], ($, Backbone, RedirectConfirmer, i18n) ->
  describe 'apps/MassUploadForm/views/RedirectConfirmer', ->
    model = undefined
    view = undefined
    redirectFunctions = undefined
    modelStatus = 'waiting'
    modelUploads = []
    $otherLink = undefined

    simulateClickOn = (el) ->
      event = document.createEvent("MouseEvents")
      event.initMouseEvent("click", true, true, window, 0, 0, 0, 0, 0, false, false, false, false, 0, null)
      el.dispatchEvent(event)
      event

    class MockMassUpload
      get: (arg) ->
        if arg == 'status'
          modelStatus
        else
          throw "invalid arg #{arg}"

      Object.defineProperty @::, 'uploads',
        get: -> modelUploads

      abort: ->

    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      $otherLink = $('<a href="#other-link">other link</a>').appendTo($('body'))
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.confirm_cancel.title': 'title'
        'views.DocumentSet._massUploadForm.confirm_cancel.prompt': 'prompt'
        'views.DocumentSet._massUploadForm.confirm_cancel.back_button': 'back_button'
        'views.DocumentSet._massUploadForm.confirm_cancel.confirm_button': 'confirm_button'
      modelStatus = 'waiting'
      modelUploads = []
      redirectFunctions =
        href: sinon.spy()
        hash: sinon.spy()
      model = new MockMassUpload
      @sandbox.stub(model, 'abort')
      view = new RedirectConfirmer
        model: model
        redirectFunctions: redirectFunctions
      view.$el.removeClass('fade') # make bootstrap synchronous

    afterEach ->
      @sandbox.restore()
      $otherLink.remove()
      view.remove()

    it 'should redirect when the MassUpload is empty', ->
      view.tryPromptAndRedirect(href: 'http://example.org')
      expect(redirectFunctions.href).to.have.been.calledWith('http://example.org')

    it 'should not intercept clicks elsewhere in the DOM when the MassUpload is empty', ->
      event = simulateClickOn($otherLink.get(0))
      expect(event.defaultPrevented).to.be(false)
      expect(redirectFunctions.href).not.to.have.been.called

    describe 'when MassUpload is not empty', ->
      beforeEach ->
        modelUploads = [ 'uploads.length will now be 1' ]
        view.tryPromptAndRedirect(href: 'http://example.org')

      it 'should not redirect right away', -> expect(redirectFunctions.href).not.to.have.been.called
      it 'should prompt', -> expect(view.$el).to.have.class('in')

      describe 'after clicking the uncancel button', ->
        beforeEach ->
          view.$('.uncancel').click()

        it 'should hide the dialog', -> expect(view.$el).not.to.have.class('in')
        it 'should not redirect', ->
          for k, __ of redirectFunctions
            expect(redirectFunctions[k]).not.to.have.been.called
        it 'should not abort', -> expect(model.abort).not.to.have.been.called

      describe 'after clicking the cancel button', ->
        beforeEach ->
          view.$('.cancel-upload').click()

        it 'should abort', -> expect(model.abort).to.have.been.called
        it 'should DELETE /files', ->
          xhr = @sandbox.server.requests[0]
          expect(xhr.method).to.eq('DELETE')
          expect(xhr.url).to.eq('/files')
        it 'should disable the buttons', ->
          expect(view.$('.cancel-upload')).to.be.disabled
          expect(view.$('.uncancel')).to.be.disabled
        it 'should not redirect yet', ->
          for k, __ of redirectFunctions
            expect(redirectFunctions[k]).not.to.have.been.called

        describe 'when DELETE succeeds', ->
          beforeEach ->
            @sandbox.server.requests[0].respond(200, {}, '')

          it 'should redirect', -> expect(redirectFunctions.href).to.have.been.calledWith('http://example.org')
          it 'should hide the dialog', -> expect(view.$el).not.to.have.class('in')

      describe 'when clicking a link elsewhere in the document', ->
        event = undefined
        beforeEach -> event = simulateClickOn($otherLink.get(0))

        it 'should preventDefault() to avoid changing URLs', -> expect(event.defaultPrevented).to.be(true)
        it 'should show the dialog', -> expect(view.$el).to.have.class('in')

        describe 'on cancel with successful DELETE', ->
          beforeEach ->
            view.$('.cancel-upload').click()
            @sandbox.server.requests[0].respond(200, {}, '')

          it 'should redirect to the link href when cancelling', ->
            expect(redirectFunctions.href).to.have.been.calledWith('#other-link')

          it 'should hide the dialog', -> expect(view.$el).not.to.have.class('in')

          describe 'next time the dialog appears', ->
            beforeEach ->
              view.tryPromptAndRedirect(href: 'http://example.org')

            it 'should have buttons enabled', -> expect(view.$('button')).not.to.be.disabled
