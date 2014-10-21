define [
  'apps/Show/views/NewVizDialog'
  'jquery'
  'i18n'
], (NewVizDialog, $, i18n) ->
  describe 'apps/Show/views/NewVizDialog', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)
      @oldFx = $.fx
      $.fx = false

      i18n.reset_messages
        'views.DocumentSet.show.NewVizDialog.title': 'title'
        'views.DocumentSet.show.NewVizDialog.cancel': 'cancel'
        'views.DocumentSet.show.NewVizDialog.submit': 'submit'
        'views.DocumentSet.show.NewVizDialog.title.label': 'name.label'
        'views.DocumentSet.show.NewVizDialog.title.placeholder': 'name.placeholder'
        'views.DocumentSet.show.NewVizDialog.url.label': 'url.label'
        'views.DocumentSet.show.NewVizDialog.url.placeholder': 'url.placeholder'
        'views.DocumentSet.show.NewVizDialog.url.checking': 'url.checking'
        'views.DocumentSet.show.NewVizDialog.url.ok': 'url.ok'
        'views.DocumentSet.show.NewVizDialog.url.unavailable_html': 'url.unavailable_html,{0},{1}'
        'views.DocumentSet.show.NewVizDialog.url.unavailable.retry': 'url.unavailable.retry'
        'views.DocumentSet.show.NewVizDialog.url.insecure_html': 'url.insecure'
        'views.DocumentSet.show.NewVizDialog.url.insecure.dismiss': 'url.insecure.dismiss'
        'views.DocumentSet.show.NewVizDialog.url.invalid': 'url.invalid'

      @submitSpy = sinon.spy()
      @$div = $('<div></div>')
      @dialog = new NewVizDialog
        container: @$div
        success: (args...) =>
          @dialog.remove()
          @dialog = null
          @submitSpy(args...)

    afterEach ->
      $.fx = @oldFx
      @dialog?.remove()
      @sandbox.restore()

    it 'should show a form', ->
      expect(@$div.find('form#new-viz-dialog')).to.exist

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
      @$div.find('input[name=url]').change()
      expect(@$div.find('input[type=submit]')).not.to.be.disabled
      @$div.find('input[type=submit]').click()
      expect(@$div.children().length).to.eq(0)
      expect(@submitSpy).to.have.been.calledWith(title: 'title', url: 'https://example.org')

    it 'should not submit when there is no title', ->
      @$div.find('input[name=title]').val('')
      @$div.find('input[name=url]').val('https://example.org')
      @dialog.statuses['https://example.org'] = 200
      @$div.find('input[name=title]').change()
      expect(@$div.find('input[type=submit]')).to.be.disabled

    it 'should not submit when there is no url', ->
      @$div.find('input[name=title]').val('title')
      @$div.find('input[name=url]').val('')
      @$div.find('input[name=url]').change()
      expect(@$div.find('input[type=submit]')).to.be.disabled

    it 'should not submit when the url is not validated', ->
      @$div.find('input[name=title]').val('title')
      @$div.find('input[name=url]').val('https://example.org')
      @$div.find('input[name=url]').change()
      expect(@$div.find('input[type=submit]')).to.be.disabled

    describe 'the url state checker', ->
      beforeEach ->
        $('body').append(@$div)
        @$url = @$div.find('input[name=url]')
        @$state = @$div.find('.state')
        @text = -> @$state.find(':visible').text()

      afterEach ->
        @$div.remove()

      it 'should show nothing by default', ->
        expect(@text()).to.eq('')

      it 'should not send a request on invalid url', ->
        @$url.val('foo bar')
        @$url.change()
        expect(@text()).to.eq('url.invalid')
        expect(@sandbox.server.requests.length).to.eq(0)

      describe 'when entering an insecure url', ->
        beforeEach ->
          @$url.val('http://example.org')
          @$url.change()

        it 'should show a warning', ->
          expect(@text()).to.contain('url.insecure')

        describe 'on dismissing the warning', ->
          beforeEach -> @$div.find('a.dismiss').click()
          it 'should hide the warning', -> expect(@text()).not.to.contain('url.insecure')
          it 'should check the url', -> expect(@sandbox.server.requests.length).to.eq(1)

      describe 'when entering a URL that starts with //', ->
        beforeEach ->
          @$url.val('//example.org')
          @$url.change()

        it 'should show checking when checking', ->
          expect(@text()).to.eq('url.checking')

        it 'should send an XHR request to /metadata when checking', ->
          req = @sandbox.server.requests[0]
          expect(req).to.exist
          expect(req.url).to.eq('http://example.org/metadata')

      describe 'when entering a secure url', ->
        beforeEach ->
          @$url.val('https://example.org')
          @$url.change()

        it 'should show checking when checking', ->
          expect(@text()).to.eq('url.checking')

        it 'should send an XHR request to /metadata when checking', ->
          req = @sandbox.server.requests[0]
          expect(req).to.exist
          expect(req.url).to.eq('https://example.org/metadata')

        it 'should have state ok when request succeeds', ->
          @sandbox.server.requests[0].respond(200)
          expect(@text()).to.eq('url.ok')

        describe 'when request fails', ->
          beforeEach -> @sandbox.server.requests[0].respond(404)

          it 'should have state unavailable when request fails', ->
            expect(@text()).to.contain('url.unavailable_html,https://example.org/metadata,404')

          it 'should let the user retry', ->
            @$div.find('.retry:visible').click()
            expect(@sandbox.server.requests[1]).to.have.property('url', 'https://example.org/metadata')
            expect(@text()).to.eq('url.checking')
            @sandbox.server.requests[1].respond(200)
            expect(@text()).to.eq('url.ok')
