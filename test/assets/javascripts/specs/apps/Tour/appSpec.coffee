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
        placement: 'auto bottom'
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

      @sandbox = sinon.sandbox.create(useFakeServer: true)

      @$html = $('<div><p class="p1">Paragraph one</p><p class="p2">Paragraph two</p><p class="p3">Paragraph three</p></div>')
        .appendTo('body')
      @app = new TourApp(tour)

    afterEach (done) ->
      @app.remove()
      @$html.remove()
      _.defer => # make sure promises settle
        @sandbox.restore()
        done()

    describe 'donePromise()', ->
      beforeEach ->
        @thenSpy = sinon.spy()
        @promise = @app.donePromise().then(@thenSpy)
        undefined # avoid mocha-as-promised

      it 'should be a promise', ->
        expect(typeof(@promise.then)).to.eq('function')

      it 'should not be resolved', (done) ->
        _.defer =>
          expect(@thenSpy).not.to.have.been.called
          done()

      describe 'when done() is called', ->
        beforeEach ->
          @app.done()
          undefined # avoid mocha-as-promised

        it 'should DELETE /tour', (done) ->
          _.defer =>
            request = @sandbox.server.requests[0]
            expect(request.url).to.eq('/tour')
            expect(request.method).to.eq('DELETE')
            done()

        it 'should not resolve donePromise before the DELETE returns', (done) ->
          _.defer =>
            expect(@thenSpy).not.to.have.been.called
            done()

        it 'should resolve donePromise when DELETE returns', (done) ->
          _.defer =>
            @sandbox.server.requests[0].respond([ 204, {}, '' ])
            _.defer =>
              expect(@thenSpy).to.have.been.called
              done()

    describe 'on load', ->
      it 'should display the first tooltip', ->
        expect($('.popover.in .popover-title')).to.contain('title1')
        expect($('.popover.in .popover-content')).to.contain('html1')

      it 'should use the provided placement', -> expect($('.popover.in.bottom')).to.be.visible
      it 'should show tip number', -> expect($('.popover.in .tip-number')).to.contain('tipNumber,1,3')
      it 'should have a next button', -> expect($('.popover.in a.next')).to.be.visible
      it 'should have a skip button', -> expect($('.popover.in a.skip')).to.be.visible
      it 'should not have a previous button', -> expect($('.popover.in a.previous')).not.to.be.visible
      it 'should not have a done button', -> expect($('.popover.in a.done')).not.to.be.visible
      it 'should not call done()', -> expect(@sandbox.server.requests.length).to.eq(0)

    describe 'when clicking skip link', ->
      beforeEach -> $('.popover.in a.skip').click()

      it 'should exit tour', ->
        expect($('.popover').length).to.eq(0)

      it 'should resolve promise', ->
        _.defer =>
          @sandbox.server.requests[0].respond(204, {}, '')
        @app.donePromise() # mocha-as-promised will fail if this fails

    describe 'after clicking next', ->
      beforeEach -> $('.popover.in a.next').click()

      it 'should switch to second tooltip', ->
        expect($('.popover.in .popover-title')).to.contain('title2')
        expect($('.popover.in .popover-content')).to.contain('html2')

      it 'should use the provided placement', -> expect($('.popover.in.right')).to.be.visible
      it 'should show tip number', -> expect($('.popover.in .tip-number')).to.contain('tipNumber,2,3')
      it 'should have a next button', -> expect($('.popover.in a.next')).to.be.visible
      it 'should have a skip button', -> expect($('.popover.in a.skip')).to.be.visible
      it 'should have a previous button', -> expect($('.popover.in a.previous')).to.be.visible
      it 'should not have a done button', -> expect($('.popover.in a.done')).not.to.be.visible

      it 'should go to previous on previous click', ->
        $('.popover.in a.previous').click()
        expect($('.popover.in .popover-title')).to.contain('title1')
        expect($('.popover.in .popover-content')).to.contain('html1')

    describe 'at last tooltip', ->
      beforeEach ->
        $('.popover.in a.next').click()
        $('.popover.in a.next').click()

      it 'should switch to the third tooltip', ->
        expect($('.popover.in .popover-title')).to.contain('title3')

      it 'should select a placement', -> expect($('.popover.bottom, .popover.top, .popover.left, .popover.right')).to.be.visible
      it 'should show tip number', -> expect($('.popover.in .tip-number')).to.contain('tipNumber,3,3')
      it 'should not have a next button', -> expect($('.popover.in a.next')).not.to.be.visible
      it 'should not have a skip button', -> expect($('.popover.in a.skip')).not.to.be.visible
      it 'should have a previous button', -> expect($('.popover.in a.previous')).to.be.visible
      it 'should have a done button', -> expect($('.popover.in a.done')).to.be.visible

    describe 'when clicking done link', ->
      beforeEach ->
        $('.popover.in a.next').click()
        $('.popover.in a.next').click()
        $('.popover.in a.done').click()

      it 'should exit tour', ->
        expect($('.popover').length).to.eq(0)

      it 'should resolve promise', ->
        _.defer =>
          @sandbox.server.requests[0].respond(204, {}, '')
        @app.donePromise() # mocha will pick up on this value

    describe 'when an element is missing', ->
      beforeEach (done) ->
        @app.remove()
        _.defer =>
          @$html.appendTo('body')
          @$html.find('.p2').remove()
          @app = new TourApp(tour)
          done()

      it 'should set the proper tip total', -> expect($('.popover.in .tip-number')).to.contain('tipNumber,1,2')

      describe 'after clicking next', ->
        beforeEach ->
          $('.popover.in a.next').click()

        it 'should set the proper tip number', -> expect($('.popover.in .tip-number')).to.contain('tipNumber,2,2')
        it 'should show the done button', -> expect($('.popover.in a.done')).to.be.visible
        it 'should show the proper tip', ->
          expect($('.popover.in .popover-title')).to.contain('title3')
          expect($('.popover.in .popover-content')).to.contain('html3')
