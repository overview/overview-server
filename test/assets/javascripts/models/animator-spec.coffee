# Not a true unit test: relies on PropertyInterpolator
PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator
Animator = require('models/animator').Animator

describe 'models/animator', ->
  describe 'Animator', ->
    interpolator = undefined
    animator = undefined

    afterEach ->
      interpolator = undefined
      animator = undefined

    describe 'with duration=0 (no animation)', ->
      beforeEach ->
        interpolator = new PropertyInterpolator(0, (x) -> x)
        animator = new Animator(interpolator)

      it 'should do nothing on update()', ->
        animator.update()

      it 'should set new values on a property without animating', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, undefined, undefined)
        expect(obj).toEqual({ x: { current: 2 } })

      it 'should still do nothing on update() after setting a property', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, undefined, undefined)
        expect(obj).toEqual({ x: { current: 2 } })
        animator.update()
        expect(obj).toEqual({ x: { current: 2 } })

      it 'should invoke callbacks immediately', ->
        called = false
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, (() -> called = true), undefined)
        expect(called).toBe(true)

      it 'should return false for needs_update()', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, (() -> called = true), undefined)
        expect(animator.needs_update()).toBe(false)

    describe 'with duration=1000 and linear interpolation', ->
      beforeEach ->
        interpolator = new PropertyInterpolator(1000, (x) -> x)
        animator = new Animator(interpolator)

      it 'should do nothing on update()', ->
        animator.update()

      it 'should return false on needs_update()', ->
        expect(animator.needs_update()).toBe(false)

      it 'should set new values on a property', ->
        obj = { x: { current: 1 } }
        Timecop.freeze new Date(100), ->
          animator.animate_object_properties(obj, { x: 2 }, undefined, undefined)
        expect(obj).toEqual({ x: { current: 1, v1: 1, v2: 2, start_ms: 100 } })

      it 'should allow the caller to specify a start_ms', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
        expect(obj).toEqual({ x: { current: 1, v1: 1, v2: 2, start_ms: 100 } })

      it 'should update() the property', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
        Timecop.freeze new Date(600), ->
          animator.update()
        expect(obj).toEqual({ x: { current: 1.5, v1: 1, v2: 2, start_ms: 100 } })

      it 'should allow update() with a time set (for testing)', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
        animator.update(600)
        expect(obj).toEqual({ x: { current: 1.5, v1: 1, v2: 2, start_ms: 100 } })

      it 'should update() a property to completion', ->
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
        animator.update(1100)
        expect(obj).toEqual({ x: { current: 2 } })

      describe 'with an ongoing animation and callback', ->
        called = undefined
        obj = undefined

        beforeEach ->
          called = false
          obj = { x: { current: 1 }, y: { current: 1 }, z: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2, y: 2 }, (() -> called = true), 100)

        it 'should invoke a callback when the animation is complete', ->
          animator.update(1099)
          expect(called).toBe(false)
          animator.update(1100)
          expect(called).toBe(true)

        it 'should return true from needs_update()', ->
          expect(animator.needs_update()).toBe(true)

        it 'should keep needs_update() true after an update that does not finish animation', ->
          animator.update(1099)
          expect(animator.needs_update()).toBe(true)

        it 'should have needs_update() false after the final update', ->
          animator.update(1100)
          expect(animator.needs_update()).toBe(false)

        it 'should not invoke the callback when changing one of the same properties', ->
          animator.animate_object_properties(obj, { y: 3 }, undefined, 600)
          animator.update(1599)
          expect(called).toBe(false)
          animator.update(1600)
          expect(called).toBe(true)

        it 'should invoke the callback when changing some other properties', ->
          animator.animate_object_properties(obj, { z: 3 }, undefined, 600)
          animator.update(1100)
          expect(called).toBe(true)
          expect(obj.z.current).toEqual(2)

        it 'switch to a second animation when needed', ->
          animator.update(600)
          expect(obj.x.current).toEqual(1.5)
          animator.animate_object_properties(obj, { x: 1 }, undefined, 600)
          expect(obj.x.current).toEqual(1.5)
          animator.update(1100)
          expect(obj.x.current).toEqual(1.25)
          animator.update(1600)
          expect(obj.x.current).toEqual(1)

        it 'should cancel the animation when setting new values directly', ->
          animator.update(600)
          animator.set_object_properties(obj, { x: 1 }, undefined, 600)
          expect(obj.x.current).toEqual(1)
          expect(obj.x.start_ms).toBeUndefined()
          animator.update(1100) # expect no crashes

        it 'should invoke the callback immediately if setting all its values directly', ->
          animator.set_object_properties(obj, { x: 3, y: 3 })
          expect(called).toBe(true)

        it 'should set needs_update to false when setting new values directly', ->
          animator.set_object_properties(obj, { x: 3, y: 3 })
          expect(animator.needs_update()).toBe(false)

        it 'should not invoke the callback when setting only some animated values directly', ->
          animator.set_object_properties(obj, { x: 1 })
          expect(called).toBe(false)

        it 'should keep needs_update true when setting only some animated values', ->
          animator.set_object_properties(obj, { x: 1 })
          expect(animator.needs_update()).toBe(true)

      it 'should invoke a callback immediately when none of the properties have changed', ->
        called = false
        obj = { x: { current: 1 } }
        animator.animate_object_properties(obj, { x: 1 }, (() -> called = true), 100)
        expect(called).toBe(true)
