define [
  'jquery'
  'backbone'
  'apps/Tree/views/FocusView'
], ($, Backbone, FocusView) ->
  Event = $.Event

  describe 'views/focus_view', ->
    describe 'FocusView', ->
      HANDLE_WIDTH=10
      div = undefined
      focus = undefined
      view = undefined

      beforeEach ->
        div = $('<div style="position:relative;width:100px;height:12px;"></div>')[0]
        focus = new Backbone.Model({ zoom: 0.5, pan: 0 })

      afterEach ->
        view?.remove()
        view = undefined
        focus = undefined
        $(div).remove()
        div = undefined

      mouse_event = (left_or_right_or_body, name, x, y) ->
        $handle = if left_or_right_or_body == 'left'
          $(div).find('.handle.left')
        else if left_or_right_or_body == 'right'
          $(div).find('.handle.right')
        else if left_or_right_or_body == 'middle'
          $(div).find('.middle')
        else
          $('body')

        position = $handle.position()
        e = Event(name)
        e.which = 1
        e.pageX = x
        e.pageY = y
        $handle.trigger(e)

      describe 'starting at (0.5 0)', ->
        beforeEach ->
          view = new FocusView({ model: focus, el: div, handleWidth: HANDLE_WIDTH })

        it 'should add handles at the edges and a middle', ->
          $handle1 = $(div).find('.handle.left')
          $handle2 = $(div).find('.handle.right')
          $middle = $(div).find('.middle')
          expect($handle1.length).to.eq(1)
          expect($handle2.length).to.eq(1)
          expect($middle.length).to.eq(1)
          expect(parseFloat($handle1.css('left'))).to.eq(20)
          expect(parseFloat($handle2.css('left')) + $handle2.outerWidth()).to.eq(80)
          expect(parseFloat($middle.css('left'))).to.eq(25)
          expect(parseFloat($middle.css('width'))).to.eq(50)

        it 'should center handles that are not at edges', ->
          $middle = view.$handles.middle
          $handle1 = view.$handles.left
          $handle2 = view.$handles.right
          expect($middle.width()).to.eq(50)
          expect(parseFloat($handle1.css('left')) + $handle1.outerWidth() / 2).to.eq(parseFloat($middle.css('left')))
          expect(parseFloat($handle2.css('left')) + $handle2.outerWidth() / 2).to.eq(parseFloat($middle.css('left')) + $middle.width())

        it 'should update when zoom changes', ->
          focus.set('zoom', 0.25)
          expect(view.$handles.left.css('left')).to.eq('32.5px')

        it 'should update when pan changes', ->
          focus.set('pan', 0.25)
          expect(view.$handles.left.css('left')).to.eq('45px')

        it 'should render when the window resizes', ->
          view.render = sinon.spy()
          $(window).trigger('resize')
          expect(view.render).to.have.been.called

        it 'should ensure HANDLE_WIDTH px between the edges', ->
          # See https://github.com/overview/overview-server/issues/266
          focus.set({ zoom: 0.000001, pan: 0 })
          $handle1 = view.$handles.left
          $handle2 = view.$handles.right
          $middle = view.$handles.middle
          expect(parseFloat($handle1.css('left')) + $handle1.outerWidth()).to.be.closeTo(50 - HANDLE_WIDTH * 0.5, 4)
          expect($middle.width()).to.eq(HANDLE_WIDTH)
          expect(parseFloat($handle2.css('left'))).to.be.closeTo(50 + HANDLE_WIDTH * 0.5, 4)

        it 'should signal when a handle is dragged', ->
          spy = sinon.spy()
          view.on('zoom-pan', spy)
          mouse_event('left', 'mousedown', 2, 5)
          mouse_event('body', 'mousemove', 12, 5) # 10 pixels to the right
          expect(spy).to.have.been.called
          zoomAndPan = spy.lastCall.args[0]
          expect(zoomAndPan.zoom).to.be.closeTo(0.4, 0.001)
          expect(zoomAndPan.pan).to.be.closeTo(0.05, 0.001)

        it 'should allow dragging past the other handle', ->
          spy = sinon.spy()
          view.on('zoom-pan', spy)
          mouse_event('right', 'mousedown', 62, 5)
          mouse_event('body', 'mousemove', 2, 5) # 60 pixels to the left
          # x1 15, x2 25
          zoomAndPan = spy.lastCall.args[0]
          expect(zoomAndPan.zoom).to.be.closeTo(0.1, 0.001)
          expect(zoomAndPan.pan).to.be.closeTo(-0.3, 0.001)

        it 'should signal when the bar is dragged', ->
          spy = sinon.spy()
          view.on('zoom-pan', spy)
          mouse_event('middle', 'mousedown', 50, 5)
          mouse_event('body', 'mousemove', 45, 5) # 5 pixels to the left -- 5% of the view
          zoomAndPan = spy.lastCall.args[0]
          expect(zoomAndPan.zoom).to.be.closeTo(0.5, 0.001) # unchanged
          expect(zoomAndPan.pan).to.be.closeTo(-0.05, 0.001)
