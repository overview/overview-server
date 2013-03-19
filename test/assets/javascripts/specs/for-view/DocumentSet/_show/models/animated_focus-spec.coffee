require [
  'for-view/DocumentSet/_show/models/animator'
  'for-view/DocumentSet/_show/models/property_interpolator'
  'for-view/DocumentSet/_show/models/animated_focus'
], (Animator, PropertyInterpolator, AnimatedFocus) ->
  # Not a unit test, because this is more useful
  describe 'models/animated_focus', ->
    describe 'AnimatedFocus', ->
      animator = undefined
      focus = undefined

      beforeEach ->
        interpolator = new PropertyInterpolator(1000, (x) -> x)
        animator = new Animator(interpolator)
        focus = new AnimatedFocus(animator)

      at = (ms, callback) -> Timecop.freeze(new Date(ms), callback)

      it 'should start at zoom 1', ->
        expect(focus.zoom).toEqual(1)

      it 'should start at pan 0', ->
        expect(focus.pan).toEqual(0)

      it 'should allow zooming to smaller fractions', ->
        focus.set_zoom(0.1)
        expect(focus.zoom).toEqual(0.1)

      it 'should animate zoom', ->
        at(0, -> focus.animate_zoom(0.5))
        at(500, -> focus.update())
        expect(focus.zoom).toEqual(0.75)
        at(1000, -> focus.update())
        expect(focus.zoom).toEqual(0.5)

      it 'should allow setting time explicitly in set_zoom', ->
        at(0, -> focus.animate_zoom(0.5, 500))
        at(1000, -> focus.update())
        expect(focus.zoom).toEqual(0.75)

      it 'should allow panning when zoomed', ->
        focus.set_zoom(0.5)
        focus.set_pan(-0.25)
        expect(focus.pan).toEqual(-0.25)

      it 'should animate pan', ->
        focus.set_zoom(0.5)
        at(0, -> focus.animate_pan(-0.25))
        at(500, -> focus.update())
        expect(focus.pan).toEqual(-0.125)
        at(1000, -> focus.update())
        expect(focus.pan).toEqual(-0.25)

      it 'should allow setting time explicitly in set_pan', ->
        focus.set_zoom(0.25)
        at(0, -> focus.animate_pan(0.25, 500))
        at(1000, -> focus.update())
        expect(focus.pan).toEqual(0.125)

      it 'should clamp zoom to max 1', ->
        focus.set_zoom(1.2)
        expect(focus.zoom).toEqual(1)

      it 'should clamp zoom above 0', ->
        focus.set_zoom(0)
        expect(focus.zoom).toNotEqual(0)

      it 'should clamp pan to 0 when zoom=1', ->
        focus.set_pan(-1)
        expect(focus.pan).toEqual(0)
        focus.set_pan(1)
        expect(focus.pan).toEqual(0)

      it 'should clamp pan to 0.375 when zoom=0.25', ->
        focus.set_zoom(0.25)
        focus.set_pan(-1)
        expect(focus.pan).toEqual(-0.375)
        focus.set_pan(1)
        expect(focus.pan).toEqual(0.375)

      it 'should always clamp pan to 0.5 non-inclusive', ->
        focus.set_zoom(0)
        focus.set_pan(-1)
        expect(focus.pan).toBeGreaterThan(-0.5)
        focus.set_pan(1)
        expect(focus.pan).toBeLessThan(0.5)

      it 'should set target_zoom', ->
        at(0, -> focus.animate_zoom(0.25))
        expect(focus.target_zoom()).toEqual(0.25)

      it 'should set target_pan', ->
        focus.set_zoom(0.25)
        at(0, -> focus.animate_pan(-0.25))
        expect(focus.target_pan()).toEqual(-0.25)

      it 'should not clamp target_zoom', ->
        at(0, -> focus.animate_zoom(0))
        expect(focus.target_zoom()).toEqual(0)

      it 'should not clamp target_pan', ->
        at(0, -> focus.animate_pan(0.5))
        expect(focus.target_pan()).toEqual(0.5)

      it 'should notify :zoom when target zoom is set', ->
        target_zoom = undefined
        focus.observe('zoom', (zoom) -> target_zoom = zoom)
        focus.set_zoom(0.25)
        expect(target_zoom).toEqual(0.25)

      it 'should notify :zoom when target zoom animation starts', ->
        target_zoom = undefined
        focus.observe('zoom', (zoom) -> target_zoom = zoom)
        focus.animate_zoom(0.25)
        expect(target_zoom).toEqual(0.25)

      it 'should notify :pan when target pan changes', ->
        target_pan = undefined
        focus.observe('pan', (pan) -> target_pan = pan)
        focus.set_pan(0.25)
        expect(target_pan).toEqual(0.25)

      it 'should notify :pan when target pan animation starts', ->
        target_pan = undefined
        focus.observe('pan', (pan) -> target_pan = pan)
        focus.animate_pan(0.25)
        expect(target_pan).toEqual(0.25)

      it 'should set sane_target_zoom', ->
        at(0, -> focus.animate_zoom(2))
        expect(focus.sane_target_zoom()).toEqual(1)

      it 'should set sane_target_pan', ->
        focus.set_zoom(0.5)
        at(0, -> focus.animate_pan(-0.5))
        expect(focus.sane_target_pan()).toEqual(-0.25)

      it 'should set needs_update=false', ->
        expect(focus.needs_update()).toBe(false)

      it 'should set needs_update=true when changing something', ->
        at(0, -> focus.animate_zoom(0.5))
        expect(focus.needs_update()).toBe(true)

      it 'should keep needs_update=false when setting something', ->
        focus.set_zoom(0.5)
        expect(focus.needs_update()).toBe(false)

      it 'should set needs_update=false when animation has finished', ->
        at(0, -> focus.animate_zoom(0.5))
        at(1000, -> focus.update())
        expect(focus.needs_update()).toBe(false)

      it 'should notify :needs-update', ->
        called = false
        focus.observe('needs-update', (() -> called = true))
        at(0, -> focus.animate_zoom(0.5))
        expect(called).toBe(true)

      it 'should start with auto_pan_zoom_enabled', ->
        expect(focus.auto_pan_zoom_enabled).toBe(true)

      it 'should set auto_pan_zoom_enabled', ->
        focus.set_auto_pan_zoom(false)
        expect(focus.auto_pan_zoom_enabled).toBe(false)
        focus.set_auto_pan_zoom(true)
        expect(focus.auto_pan_zoom_enabled).toBe(true)

      it 'should block_auto_pan_zoom() and unblock_auto_pan_zoom()', ->
        focus.block_auto_pan_zoom()
        expect(focus.auto_pan_zoom_enabled).toBe(false)
        focus.unblock_auto_pan_zoom()
        expect(focus.auto_pan_zoom_enabled).toBe(true)

      it 'should allow nested block_auto_pan_zoom()', ->
        focus.block_auto_pan_zoom()
        focus.block_auto_pan_zoom()
        focus.unblock_auto_pan_zoom()
        expect(focus.auto_pan_zoom_enabled).toBe(false)
        focus.unblock_auto_pan_zoom()
        expect(focus.auto_pan_zoom_enabled).toBe(true)

      it 'should postpone set_auto_pan_zoom() when blocked', ->
        focus.set_auto_pan_zoom(false)
        focus.block_auto_pan_zoom()
        focus.set_auto_pan_zoom(true)
        expect(focus.auto_pan_zoom_enabled).toBe(false)
        focus.unblock_auto_pan_zoom()
        expect(focus.auto_pan_zoom_enabled).toBe(true)

      it 'should auto_fit_pan()', ->
        focus.set_zoom_and_pan(0.1, 0.0)
        focus.auto_fit_pan(-0.5, -0.4)
        expect(focus.zoom).toEqual(0.1)
        expect(focus.pan).toEqual(-0.45)

      it 'should not auto_fit_pan() if auto_pan_zoom_enabled is false', ->
        focus.set_zoom_and_pan(0.1, 0.0)
        focus.block_auto_pan_zoom('test')
        focus.auto_fit_pan(-0.5, -0.2)
        expect(focus.zoom).toEqual(0.1)
        expect(focus.pan).toEqual(0.0)

      it 'should auto_fit_pan() and zoom out when necessary', ->
        focus.set_zoom_and_pan(0.1, 0.0)
        focus.auto_fit_pan(-0.2, 0.0)
        expect(focus.zoom).toEqual(0.2)
        expect(focus.pan).toEqual(-0.1)

      it 'should do nothing in auto_fit_pan() when the pan fits', ->
        focus.set_zoom_and_pan(0.1, 0.01)
        focus.auto_fit_pan(-0.002, 0.01)
        expect(focus.zoom).toEqual(0.1)
        expect(focus.pan).toEqual(0.01)

      it 'should pan right as little as possible in auto_fit_pan()', ->
        focus.set_zoom_and_pan(0.1, 0.0)
        focus.auto_fit_pan(0.2, 0.25)
        expect(focus.pan).toEqual(0.2) # i.e., range is 0.15-0.25, on the edge

      it 'should pan left as little as possible in auto_fit_pan()', ->
        focus.set_zoom_and_pan(0.1, 0.0)
        focus.auto_fit_pan(-0.25, -0.2)
        expect(focus.pan).toEqual(-0.2) # i.e., range is -0.25--0.15, on the edge
