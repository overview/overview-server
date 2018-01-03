define [
  'apps/Show/views/NewViewDialog'
  'jquery'
  'i18n'
], (NewViewDialog, $, i18n) ->
  describe 'apps/Show/views/NewViewDialog', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @oldFx = $.fx
      $.fx = false

      i18n.reset_messages
        'views.DocumentSet.show.NewViewDialog.title': 'title'
        'views.DocumentSet.show.NewViewDialog.cancel': 'cancel'
        'views.DocumentSet.show.NewViewDialog.submit': 'submit'
        'views.DocumentSet.show.NewViewDialog.title.label': 'name.label'
        'views.DocumentSet.show.NewViewDialog.title.placeholder': 'name.placeholder'
        'views.DocumentSet.show.NewViewDialog.url.label': 'url.label'
        'views.DocumentSet.show.NewViewDialog.url.placeholder': 'url.placeholder'
        'views.DocumentSet.show.NewViewDialog.url.checking': 'url.checking'
        'views.DocumentSet.show.NewViewDialog.url.ok': 'url.ok'
        'views.DocumentSet.show.NewViewDialog.url.unavailable_html': 'url.unavailable_html,{0},{1}'
        'views.DocumentSet.show.NewViewDialog.url.unavailable.retry': 'url.unavailable.retry'
        'views.DocumentSet.show.NewViewDialog.url.insecure_html': 'url.insecure'
        'views.DocumentSet.show.NewViewDialog.url.insecure.dismiss': 'url.insecure.dismiss'
        'views.DocumentSet.show.NewViewDialog.url.invalid': 'url.invalid'
        'views.DocumentSet.show.NewViewDialog.url.readHtmlMessage': 'url.readHtmlMessage'
        'views.DocumentSet.show.NewViewDialog.serverUrlFromPlugin.label': 'serverUrlFromPlugin.label'
        'views.DocumentSet.show.NewViewDialog.serverUrlFromPlugin.help': 'serverUrlFromPlugin.help'

      @submitSpy = sinon.spy()
      @$div = $('<div></div>')
      @dialog = new NewViewDialog
        container: @$div
        success: (args...) =>
          @dialog.remove()
          @dialog = null
          @submitSpy(args...)
      @form = @$div.children()[0]

      @assertSubmitFail = =>
        @$div.find('input[type=submit]').click()
        expect(@form.checkValidity()).to.be.false
        expect(@submitSpy).not.to.have.been.called

    afterEach ->
      $.fx = @oldFx
      @dialog?.remove()
      @sandbox.restore()

    it 'should show a form', ->
      expect(@$div.find('form#new-view-dialog')).to.exist

    it 'should remove the form', ->
      @dialog.remove()
      expect(@$div.children().length).to.eq(0)

    it 'should cancel', ->
      @$div.find('input[type=reset]').click()
      expect(@$div.children().length).to.eq(0)

    it 'should submit and disappear', ->
      @$div.find('input[name=title]').val('title')
      @$div.find('input[name=url]').val('https://example.org')
      @dialog.statuses['https://example.org'] = 200
      @$div.find('input[type=submit]').click()
      expect(@$div.children().length).to.eq(0)
      expect(@submitSpy).to.have.been.calledWith(title: 'title', url: 'https://example.org', serverUrlFromPlugin: '')

    it 'should not submit when there is no title', ->
      @$div.find('input[name=title]').val('')
      @$div.find('input[name=url]').val('https://example.org')
      @dialog.statuses['https://example.org'] = 200
      @assertSubmitFail()

    it 'should not submit when there is no url', ->
      @$div.find('input[name=title]').val('title')
      @$div.find('input[name=url]').val('')
      @assertSubmitFail()

    it 'should not submit when the url is not validated', ->
      @$div.find('input[name=title]').val('title')
      @$div.find('input[name=url]').val('https://example.org').change()
      @assertSubmitFail()

    describe 'the url state checker', ->
      beforeEach ->
        $('body').append(@$div)
        @$div.find('input[name=title]').val('title') # make it valid
        @$url = @$div.find('input[name=url]')
        @$state = @$div.find('.state')
        @text = -> @$state.find(':visible').text()

      afterEach ->
        @$div.remove()

      it 'should show nothing by default', ->
        expect(@text()).to.eq('')

      it 'should not send a request on invalid url', ->
        @$url.val('foo bar')
        @assertSubmitFail()
        expect(@sandbox.server.requests.length).to.eq(0)

      describe 'when entering an insecure url', ->
        beforeEach ->
          @$url.val('http://example.org')
          @assertSubmitFail()

        it 'should show a warning', ->
          expect(@text()).to.contain('url.insecure')

        it 'should not allow submitting', ->
          @assertSubmitFail()

        describe 'on dismissing the warning', ->
          beforeEach -> @$div.find('a.dismiss').click()
          it 'should hide the warning', -> expect(@text()).not.to.contain('url.insecure')
          it 'should check the url', -> expect(@sandbox.server.requests.length).to.eq(1)

      describe 'when entering a secure url', ->
        beforeEach ->
          @$url.val('https://example.org')
          @assertSubmitFail()

        it 'should show checking when checking', ->
          expect(@text()).to.eq('url.checking')

        it 'should not allow submitting before url check completes', ->
          @assertSubmitFail()

        it 'should send an XHR request to /metadata when checking', ->
          req = @sandbox.server.requests[0]
          expect(req).to.exist
          expect(req.url).to.eq('https://example.org/metadata')

        it 'should become valid when the request succeeds', ->
          @$div.find('input[name=title]').val('') # make whole form invalid, so it doesn't submit
          @sandbox.server.requests[0].respond(200)
          expect(@$url[0].checkValidity()).to.be.true

        it 'should auto-submit when the request succeeds', ->
          @sandbox.server.requests[0].respond(200)
          expect(@submitSpy).to.have.been.called

        it 'should not submit when serverUrlFromPlugin is invalid', ->
          @$div[0].querySelector('input[name=serverUrlFromPlugin]').value = 'http//overview-web'
          @sandbox.server.requests[0].respond(200)
          @assertSubmitFail()

        describe 'when request fails', ->
          beforeEach -> @sandbox.server.requests[0].respond(404)

          it 'should have state unavailable when request fails', ->
            expect(@text()).to.contain('url.unavailable_html,https://example.org/metadata,404')

          it 'should not allow submitting', ->
            @assertSubmitFail()

          it 'should allow correcting and failing with the second corrected value', ->
            @assertSubmitFail()
            @$url.val('https://example2.org').change()
            @assertSubmitFail()
            @sandbox.server.requests[1].respond(404)
            @assertSubmitFail()

          it 'should allow correcting', ->
            @assertSubmitFail()
            @$url.val('https://example2.org').change()
            @assertSubmitFail()
            @sandbox.server.requests[1].respond(200)
            @$div.find('input[type=submit]').click()
            expect(@$div.children().length).to.eq(0)
            expect(@submitSpy).to.have.been.calledWith(title: 'title', url: 'https://example2.org', serverUrlFromPlugin: '')

          it 'should let the user retry', ->
            @$div.find('.retry:visible').click()
            expect(@sandbox.server.requests[1]).to.have.property('url', 'https://example.org/metadata')
            expect(@text()).to.eq('url.checking')
            @sandbox.server.requests[1].respond(200)
            expect(@text()).to.eq('url.ok')
