# Not a unit test, because this is more useful
Animator = require('models/animator').Animator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator
AnimatedFocus = require('models/animated_focus').AnimatedFocus

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
