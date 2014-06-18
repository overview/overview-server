define [
  'underscore'
  'jquery'
  'apps/Tour/app'
  'i18n'
], (_, $, TourApp, i18n) ->
  describe 'apps/Tour/app', ->
    tour = [
      {
        find: 'p.p1'
        title: 'title1'
        placement: 'bottom'
        bodyHtml: '<p>html1</p>'
      }
      {
        find: 'p.p2'
        title: 'title2'
        placement: 'right'
        bodyHtml: '<p>html2</p>'
      }
      {
        find: 'p.p3'
        title: 'title3'
        placement: 'top'
        bodyHtml: '<p>html3</p>'
      }
    ]

    beforeEach ->
      i18n.reset_messages
        'views.Tree.show.Tour.next': 'next'
        'views.Tree.show.Tour.previous': 'previous'
        'views.Tree.show.Tour.done': 'done'
        'views.Tree.show.Tour.skip': 'skip'
        'views.Tree.show.Tour.tipNumber': 'tipNumber,{0},{1}'

      # We tick manually. Otherwise there's a big delay in resolving promises
      @sandbox = sinon.sandbox.create(useFakeServer: true, useFakeTimers: true)

    afterEach ->
      @sandbox.clock.tick(1)
      for req in @sandbox.server.requests when !req.status?
        req.respond(500, {}, '')
      @sandbox.clock.tick(1)
      @sandbox.restore()

    describe 'when all elements are present', ->
      beforeEach ->
        @$html = $('<div><p class="p1">Paragraph one</p><p class="p2">Paragraph two</p><p class="p3">Paragraph three</p></div>')
        @app = new TourApp(tour, skipRepaint: true, container: @$html)

      afterEach ->
        @app.remove()

      describe 'donePromise()', ->
        beforeEach ->
          @thenSpy = sinon.spy()
          @promise = @app.donePromise().then(@thenSpy)
          undefined # avoid mocha-as-promised

        it 'should be a promise', ->
          expect(typeof(@promise.then)).to.eq('function')

        it 'should not be resolved', ->
          @sandbox.clock.tick(1)
          expect(@thenSpy).not.to.have.been.called

        describe 'when done() is called', ->
          beforeEach ->
            @app.done()
            @sandbox.clock.tick(1)

          it 'should DELETE /tour', ->
            request = @sandbox.server.requests[0]
            expect(request.url).to.eq('/tour')
            expect(request.method).to.eq('DELETE')

          it 'should not resolve donePromise before the DELETE returns', ->
            expect(@thenSpy).not.to.have.been.called

          it 'should resolve donePromise when DELETE returns', ->
            @sandbox.server.requests[0].respond([ 204, {}, '' ])
            @sandbox.clock.tick(1)
            expect(@thenSpy).to.have.been.called

      describe 'on load', ->
        it 'should display the first tooltip', ->
          expect(@$html.find('.popover.in .popover-title')).to.contain('title1')
          expect(@$html.find('.popover.in .popover-content')).to.contain('html1')
          expect(@$html.find('.popover.in.bottom')).to.exist

        it 'should have next and skip buttons', ->
          expect(@$html.find('.popover.in .tip-number')).to.contain('tipNumber,1,3')
          expect(@$html.find('.popover.in a.next')).to.exist
          expect(@$html.find('.popover.in a.skip')).to.exist
          expect(@$html.find('.popover.in a.previous')).not.to.exist
          expect(@$html.find('.popover.in a.done')).not.to.exist

        it 'should not call done()', -> expect(@sandbox.server.requests.length).to.eq(0)

      it 'should exit tour and resolve promise when clicking skip', ->
        spy = sinon.spy()
        @app.donePromise().then(spy)

        @$html.find('.popover.in a.skip').click()
        expect(@$html.find('.popover').length).to.eq(0)

        @sandbox.clock.tick(1)
        @sandbox.server.requests[0].respond(204, {}, '')
        @sandbox.clock.tick(1)
        expect(spy).to.have.been.called

      describe 'after clicking next', ->
        beforeEach -> @$html.find('.popover.in a.next').click()

        it 'should switch to second tooltip', ->
          expect(@$html.find('.popover.in .popover-title')).to.contain('title2')
          expect(@$html.find('.popover.in .popover-content')).to.contain('html2')

        it 'should have next and skip buttons', ->
          expect(@$html.find('.popover.in .tip-number')).to.contain('tipNumber,2,3')
          expect(@$html.find('.popover.in a.next')).to.exist
          expect(@$html.find('.popover.in a.skip')).to.exist
          expect(@$html.find('.popover.in a.previous')).to.exist
          expect(@$html.find('.popover.in a.done')).not.to.exist

        it 'should go to previous on previous click', ->
          @$html.find('.popover.in a.previous').click()
          expect(@$html.find('.popover.in .popover-title')).to.contain('title1')
          expect(@$html.find('.popover.in .popover-content')).to.contain('html1')

      describe 'at last tooltip', ->
        beforeEach ->
          @$html.find('.popover.in a.next').click()
          @$html.find('.popover.in a.next').click()

        it 'should switch to the third tooltip', ->
          expect(@$html.find('.popover.in .popover-title')).to.contain('title3')

        it 'should hage previous and done buttons', ->
          expect(@$html.find('.popover.in .tip-number')).to.contain('tipNumber,3,3')
          expect(@$html.find('.popover.in a.next')).not.to.exist
          expect(@$html.find('.popover.in a.skip')).not.to.exist
          expect(@$html.find('.popover.in a.previous')).to.exist
          expect(@$html.find('.popover.in a.done')).to.exist

      it 'should exit tour and resolve promise when clicking done link', ->
        spy = sinon.spy()
        @app.donePromise().then(spy)

        @$html.find('.popover.in a.next').click()
        @$html.find('.popover.in a.next').click()
        @$html.find('.popover.in a.done').click()
        @sandbox.clock.tick(1)

        expect(@$html.find('.popover')).not.to.exist

        @sandbox.server.requests[0].respond(204, {}, '')
        @sandbox.clock.tick(1)
        expect(spy).to.have.been.called

    describe 'when an element is missing', ->
      beforeEach ->
        @$html = $('<div><p class="p1">Paragraph one</p><p class="p3">Paragraph three</p></div>')
        @app = new TourApp(tour, skipRepaint: true, container: @$html)

      afterEach ->
        @app.remove()

      it 'should set the proper tip total', ->
        expect(@$html.find('.popover.in .tip-number')).to.contain('tipNumber,1,2')

      describe 'after clicking next', ->
        beforeEach ->
          @$html.find('.popover.in a.next').click()

        it 'should show the done button', -> expect(@$html.find('.popover.in a.done')).to.exist

        it 'should show the proper tip', ->
          expect(@$html.find('.popover.in .tip-number')).to.contain('tipNumber,2,2')
          expect(@$html.find('.popover.in .popover-title')).to.contain('title3')
          expect(@$html.find('.popover.in .popover-content')).to.contain('html3')
