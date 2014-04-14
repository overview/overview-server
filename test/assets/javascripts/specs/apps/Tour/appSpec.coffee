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

      jasmine.Ajax.install()

      @$html = $('<div><p class="p1">Paragraph one</p><p class="p2">Paragraph two</p><p class="p3">Paragraph three</p></div>')
        .appendTo('body')
      @app = new TourApp(tour)

    afterEach ->
      @app.remove()
      @$html.remove()
      jasmine.Ajax.uninstall()

    describe 'donePromise()', ->
      beforeEach ->
        @thenSpy = jasmine.createSpy('then')
        @promise = @app.donePromise().then(@thenSpy)

      it 'should be a promise', ->
        expect(typeof(@promise.then)).toEqual('function')

      it 'should not be resolved', (done) ->
        _.defer =>
          expect(@thenSpy).not.toHaveBeenCalled()
          done()

      describe 'when done() is called', ->
        beforeEach -> @app.done()

        it 'should DELETE /tour', (done) ->
          _.defer =>
            request = jasmine.Ajax.requests.mostRecent()
            expect(request.url).toEqual('/tour')
            expect(request.method).toEqual('DELETE')
            done()

        it 'should not resolve donePromise before the DELETE returns', (done) ->
          _.defer =>
            expect(@thenSpy).not.toHaveBeenCalled()
            done()

        it 'should resolve donePromise when DELETE returns', (done) ->
          _.defer =>
            jasmine.Ajax.requests.mostRecent().response(status: 204)
            _.defer =>
              expect(@thenSpy).toHaveBeenCalled()
              done()

    describe 'on load', ->
      it 'should display the first tooltip', ->
        expect($('.popover.in .popover-title')).toContainText('title1')
        expect($('.popover.in .popover-content')).toContainText('html1')

      it 'should use the provided placement', -> expect($('.popover.in.bottom')).toBeVisible()
      it 'should show tip number', -> expect($('.popover.in .tip-number')).toContainText('tipNumber,1,3')
      it 'should have a next button', -> expect($('.popover.in a.next')).toBeVisible()
      it 'should have a skip button', -> expect($('.popover.in a.skip')).toBeVisible()
      it 'should not have a previous button', -> expect($('.popover.in a.previous')).not.toBeVisible()
      it 'should not have a done button', -> expect($('.popover.in a.done')).not.toBeVisible()

    describe 'when clicking skip link', ->
      beforeEach -> $('.popover.in a.skip').click()

      it 'should exit tour', ->
        expect($('.popover')).not.toBeInDOM()

      it 'should resolve promise', (done) ->
        spy = jasmine.createSpy('then')
        @app.donePromise().done(spy)
        _.defer ->
          jasmine.Ajax.requests.mostRecent().response(status: 204)
          _.defer ->
            expect(spy).toHaveBeenCalled()
            done()

    describe 'after clicking next', ->
      beforeEach -> $('.popover.in a.next').click()

      it 'should switch to second tooltip', ->
        expect($('.popover.in .popover-title')).toContainText('title2')
        expect($('.popover.in .popover-content')).toContainText('html2')

      it 'should use the provided placement', -> expect($('.popover.in.right')).toBeVisible()
      it 'should show tip number', -> expect($('.popover.in .tip-number')).toContainText('tipNumber,2,3')
      it 'should have a next button', -> expect($('.popover.in a.next')).toBeVisible()
      it 'should have a skip button', -> expect($('.popover.in a.skip')).toBeVisible()
      it 'should have a previous button', -> expect($('.popover.in a.previous')).toBeVisible()
      it 'should not have a done button', -> expect($('.popover.in a.done')).not.toBeVisible()

      it 'should go to previous on previous click', ->
        $('.popover.in a.previous').click()
        expect($('.popover.in .popover-title')).toContainText('title1')
        expect($('.popover.in .popover-content')).toContainText('html1')

    describe 'at last tooltip', ->
      beforeEach ->
        $('.popover.in a.next').click()
        $('.popover.in a.next').click()

      it 'should switch to the third tooltip', ->
        expect($('.popover.in .popover-title')).toContainText('title3')

      it 'should select a placement', -> expect($('.popover.bottom, .popover.top, .popover.left, .popover.right')).toBeVisible()
      it 'should show tip number', -> expect($('.popover.in .tip-number')).toContainText('tipNumber,3,3')
      it 'should not have a next button', -> expect($('.popover.in a.next')).not.toBeVisible()
      it 'should not have a skip button', -> expect($('.popover.in a.skip')).not.toBeVisible()
      it 'should have a previous button', -> expect($('.popover.in a.previous')).toBeVisible()
      it 'should have a done button', -> expect($('.popover.in a.done')).toBeVisible()

    describe 'when clicking done link', ->
      beforeEach ->
        $('.popover.in a.next').click()
        $('.popover.in a.next').click()
        $('.popover.in a.done').click()

      it 'should exit tour', ->
        expect($('.popover')).not.toBeInDOM()

      it 'should resolve promise', (done) ->
        spy = jasmine.createSpy('then')
        @app.donePromise().done(spy)
        _.defer ->
          jasmine.Ajax.requests.mostRecent().response(status: 204)
          _.defer ->
            expect(spy).toHaveBeenCalled()
            done()

    describe 'when an element is missing', ->
      beforeEach ->
        @app.remove()
        @$html.appendTo('body')
        @$html.find('.p2').remove()
        @app = new TourApp(tour)

      it 'should set the proper tip total', -> expect($('.popover.in .tip-number')).toContainText('tipNumber,1,2')

      describe 'after clicking next', ->
        beforeEach ->
          $('.popover.in a.next').click()

        it 'should set the proper tip number', -> expect($('.popover.in .tip-number')).toContainText('tipNumber,2,2')
        it 'should show the done button', -> expect($('.popover.in a.done')).toBeVisible()
        it 'should show the proper tip', ->
          expect($('.popover.in .popover-title')).toContainText('title3')
          expect($('.popover.in .popover-content')).toContainText('html3')
