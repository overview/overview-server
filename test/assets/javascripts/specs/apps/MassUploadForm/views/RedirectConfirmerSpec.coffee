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
      jasmine.Ajax.install()
      $otherLink = $('<a href="#other-link">other link</a>').appendTo($('body'))
      i18n.reset_messages
        'views.DocumentSet._massUploadForm.confirm_cancel.title': 'title'
        'views.DocumentSet._massUploadForm.confirm_cancel.prompt': 'prompt'
        'views.DocumentSet._massUploadForm.confirm_cancel.back_button': 'back_button'
        'views.DocumentSet._massUploadForm.confirm_cancel.confirm_button': 'confirm_button'
      modelStatus = 'waiting'
      modelUploads = []
      redirectFunctions =
        href: jasmine.createSpy('redirectCalls.href')
        hash: jasmine.createSpy('redirectCalls.hash')
      model = new MockMassUpload
      spyOn(model, 'abort')
      view = new RedirectConfirmer
        model: model
        redirectFunctions: redirectFunctions
      view.$el.removeClass('fade') # make bootstrap synchronous

    afterEach ->
      jasmine.Ajax.uninstall()
      $otherLink.remove()
      view.remove()

    it 'should redirect when the MassUpload is empty', ->
      view.tryPromptAndRedirect(href: 'http://example.org')
      expect(redirectFunctions.href).toHaveBeenCalledWith('http://example.org')

    it 'should not intercept clicks elsewhere in the DOM when the MassUpload is empty', ->
      event = simulateClickOn($otherLink.get(0))
      expect(event.defaultPrevented).toBe(false)
      expect(redirectFunctions.href).not.toHaveBeenCalled()

    describe 'when MassUpload is not empty', ->
      beforeEach ->
        modelUploads = [ 'uploads.length will now be 1' ]
        view.tryPromptAndRedirect(href: 'http://example.org')

      it 'should not redirect right away', -> expect(redirectFunctions.href).not.toHaveBeenCalled()
      it 'should prompt', -> expect(view.el).toHaveClass('in')

      describe 'after clicking the uncancel button', ->
        beforeEach ->
          view.$('.uncancel').click()

        it 'should hide the dialog', -> expect(view.el).not.toHaveClass('in')
        it 'should not redirect', ->
          for k, __ of redirectFunctions
            expect(redirectFunctions[k]).not.toHaveBeenCalled()
        it 'should not abort', -> expect(model.abort).not.toHaveBeenCalled()

      describe 'after clicking the cancel button', ->
        beforeEach ->
          view.$('.cancel-upload').click()

        it 'should abort', -> expect(model.abort).toHaveBeenCalled()
        it 'should DELETE /files', ->
          xhr = jasmine.Ajax.requests.mostRecent()
          expect(xhr.method).toEqual('DELETE')
          expect(xhr.url).toEqual('/files')
        it 'should disable the buttons', ->
          expect(view.$('.cancel-upload')).toBeDisabled()
          expect(view.$('.uncancel')).toBeDisabled()
        it 'should not redirect yet', ->
          for k, __ of redirectFunctions
            expect(redirectFunctions[k]).not.toHaveBeenCalled()

        describe 'when DELETE succeeds', ->
          beforeEach ->
            jasmine.Ajax.requests.mostRecent().response(status: 200, responseText: '')

          it 'should redirect', -> expect(redirectFunctions.href).toHaveBeenCalledWith('http://example.org')
          it 'should hide the dialog', -> expect(view.$el).not.toHaveClass('in')

      describe 'when clicking a link elsewhere in the document', ->
        event = undefined
        beforeEach -> event = simulateClickOn($otherLink.get(0))

        it 'should preventDefault() to avoid changing URLs', -> expect(event.defaultPrevented).toBe(true)
        it 'should show the dialog', -> expect(view.el).toHaveClass('in')

        describe 'on cancel with successful DELETE', ->
          beforeEach ->
            view.$('.cancel-upload').click()
            jasmine.Ajax.requests.mostRecent().response(status: 200, responseText: '')

          it 'should redirect to the link href when cancelling', ->
            expect(redirectFunctions.href).toHaveBeenCalledWith('#other-link')

          it 'should hide the dialog', -> expect(view.$el).not.toHaveClass('in')

          describe 'next time the dialog appears', ->
            beforeEach ->
              view.tryPromptAndRedirect(href: 'http://example.org')

            it 'should have buttons enabled', -> expect(view.$('button')).not.toBeDisabled()
