define [
  'apps/Tree/models/animator'
  'apps/Tree/models/property_interpolator'
  'apps/Tree/models/AnimatedFocus'
  'apps/Tree/models/AnimatedNode'
  'apps/Tree/models/AnimatedTree'
], (Animator, PropertyInterpolator, AnimatedFocus, AnimatedNode, AnimatedTree) ->
  # Not a unit test, because this is more useful
  describe 'models/animated_focus', ->
    describe 'AnimatedFocus', ->
      animator = undefined
      focus = undefined
      sandbox = undefined

      beforeEach ->
        interpolator = new PropertyInterpolator(1000, (x) -> x)
        animator = new Animator(interpolator)
        focus = new AnimatedFocus({ zoom: 0.5, pan: 0 }, { animator: animator })
        sandbox = sinon.sandbox.create(useFakeTimers: true)

      afterEach ->
        sandbox.restore()

      it 'should set target zoom and pan', ->
        focus.setPanAndZoom(0.25, 0.1)
        expect(focus.nextObject).to.deep.eq({ pan: 0.25, zoom: 0.1 })

      it 'sets pan and zoom for a specific node', ->
        node = new AnimatedNode({id: 2, parentId: null, description: "some words", size: 10, isLeaf: true}, null)
        animatedTree =
          calculateBounds: -> { right: 25, left: -75 }
          bounds: { right: 100, left: -100 }

        focus.animateNode(node)
        focus.fraction = {current: 1}
        focus.update(animatedTree, 1000)

        expect(focus.get('pan')).to.eq(-0.125)
        expect(focus.get('zoom')).to.eq(0.5)

      it 'zooms the node less when the narrow property is set', ->
        node = new AnimatedNode({id: 2, parentId: null, description: "some words", size: 10, isLeaf: true}, null)
        node.narrow = true
        node.position = {xMiddle: 5}
        animatedTree =
          calculateBounds: -> { right: 25, left: -75 }
          bounds: { right: 100, left: -100 }

        focus.animateNode(node)
        focus.fraction = {current: 1}
        focus.update(animatedTree, 1000)

        expect(focus.get('pan')).to.eq(-0.125)
        expect(focus.get('zoom')).to.eq(2)

      it 'pans narrow nodes on right edge of the tree to the right edge of the viewport', ->
        node = new AnimatedNode({id: 2, parentId: null, description: "some words", size: 10, isLeaf: true}, null)
        node.narrow = true
        node.position = {xMiddle: 95}
        animatedTree =
          calculateBounds: -> { right: 25, left: -75 }
          bounds: { right: 100, left: -100 }

        focus.animateNode(node)
        focus.fraction = {current: 1}
        focus.update(animatedTree, 1000)

        expect(focus.get('pan')).to.eq(-0.875)
        expect(focus.get('zoom')).to.eq(2)

      it 'pans narrow nodes on left edge of the tree to the left edge of the viewport', ->
        node = new AnimatedNode({id: 2, parentId: null, description: "some words", size: 10, isLeaf: true}, null)
        node.narrow = true
        node.position = {xMiddle: -95}
        animatedTree =
          calculateBounds: -> { right: 25, left: -75 }
          bounds: { right: 100, left: -100 }

        focus.animateNode(node)
        focus.fraction = {current: 1}
        focus.update(animatedTree, 1000)

        expect(focus.get('pan')).to.eq(0.625)
        expect(focus.get('zoom')).to.eq(2)

      it 'should allow setting time explicitly for animation', ->
        focus.animatePanAndZoom(0.25, 0.1, 500)
        focus.update(undefined, 1000)
        expect(focus.get('pan')).to.eq(0.125)

      it 'should clamp zoom above 0', ->
        focus.setPanAndZoom(0, 0)
        expect(focus.nextObject.zoom).to.be.greaterThan(0)

      it 'should clamp zoom to max 1', ->
        focus.setPanAndZoom(0, 1.2)
        expect(focus.nextObject.zoom).to.eq(1)

      it 'should clamp pan to 0 when zoom=1', ->
        focus.setPanAndZoom(-1, 1)
        expect(focus.nextObject.pan).to.eq(0)
        focus.setPanAndZoom(1, 1)
        expect(focus.nextObject.pan).to.eq(0)

      it 'should clamp pan to 0.375 when zoom=0.25', ->
        focus.setPanAndZoom(-1, 0.25)
        expect(focus.nextObject.pan).to.eq(-0.375)
        focus.setPanAndZoom(1, 0.25)
        expect(focus.nextObject.pan).to.eq(0.375)

      it 'should always clamp pan to 0.5 non-inclusive', ->
        focus.setPanAndZoom(0, -1)
        expect(focus.nextObject.pan).to.be.greaterThan(-0.5)
        focus.setPanAndZoom(0, 1)
        expect(focus.nextObject.pan).to.be.lessThan(0.5)

      it 'should notify when zoom is set', ->
        spy = sinon.spy()
        focus.on('change', spy)
        focus.setZoom(0.1)
        expect(spy).to.have.been.called

      it 'should notify when zoom animation starts', ->
        spy = sinon.spy()
        focus.on('change', spy)
        focus.animateZoom(0.1)
        expect(spy).to.have.been.called

      it 'should notify when pan changes', ->
        spy = sinon.spy()
        focus.on('change', spy)
        focus.setPan(0.25)
        expect(spy).to.have.been.called

      it 'should notify when pan animation starts', ->
        spy = sinon.spy()
        focus.on('change', spy)
        focus.animatePan(0.1)
        expect(spy).to.have.been.called

      it 'should set needs_update=false', ->
        expect(focus.needsUpdate()).to.be(false)

      it 'should set needs_update=true when changing something', ->
        focus.animateZoom(0.1)
        sandbox.clock.tick(1)
        expect(focus.needsUpdate()).to.be(true)

      it 'should keep needs_update=false when setting something', ->
        focus.setZoom(0.1)
        expect(focus.needsUpdate()).to.be(false)

      it 'should set needs_update=false when animation has finished', ->
        focus.animateZoom(0.1)
        sandbox.clock.tick(1000)
        focus.update()
        sandbox.clock.tick(10)
        expect(focus.needsUpdate()).to.be(false)
