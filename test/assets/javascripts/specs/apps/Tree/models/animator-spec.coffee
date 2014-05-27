# Not a true unit test: relies on PropertyInterpolator
define [
  'apps/Tree/models/property_interpolator'
  'apps/Tree/models/animator'
], (PropertyInterpolator, Animator) ->
  describe 'models/animator', ->
    describe 'Animator', ->
      interpolator = undefined
      animator = undefined

      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeTimers: true)

      afterEach ->
        @sandbox.restore()

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
          expect(obj).to.deep.eq({ x: { current: 2 } })

        it 'should still do nothing on update() after setting a property', ->
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, undefined, undefined)
          expect(obj).to.deep.eq({ x: { current: 2 } })
          animator.update()
          expect(obj).to.deep.eq({ x: { current: 2 } })

        it 'should invoke callbacks immediately', ->
          called = false
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, (() -> called = true), undefined)
          expect(called).to.be(true)

        it 'should return false for needs_update()', ->
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, (() -> called = true), undefined)
          expect(animator.needs_update()).to.be(false)

      describe 'with duration=1000 and linear interpolation', ->
        beforeEach ->
          interpolator = new PropertyInterpolator(1000, (x) -> x)
          animator = new Animator(interpolator)

        it 'should do nothing on update()', ->
          animator.update()

        it 'should return false on needs_update()', ->
          expect(animator.needs_update()).to.be(false)

        it 'should set new values on a property', ->
          obj = { x: { current: 1 } }
          @sandbox.clock.tick(100)
          animator.animate_object_properties(obj, { x: 2 }, undefined, undefined)
          expect(obj).to.deep.eq({ x: { current: 1, v1: 1, v2: 2, start_ms: 100 } })

        it 'should allow the caller to specify a start_ms', ->
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
          expect(obj).to.deep.eq({ x: { current: 1, v1: 1, v2: 2, start_ms: 100 } })

        it 'should update() the property', ->
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
          @sandbox.clock.tick(600)
          animator.update()
          expect(obj).to.deep.eq({ x: { current: 1.5, v1: 1, v2: 2, start_ms: 100 } })

        it 'should allow update() with a time set (for testing)', ->
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
          animator.update(600)
          expect(obj).to.deep.eq({ x: { current: 1.5, v1: 1, v2: 2, start_ms: 100 } })

        it 'should update() a property to completion', ->
          obj = { x: { current: 1 } }
          animator.animate_object_properties(obj, { x: 2 }, undefined, 100)
          animator.update(1100)
          expect(obj).to.deep.eq({ x: { current: 2 } })

        describe 'with an ongoing animation and callback', ->
          called = undefined
          obj = undefined

          beforeEach ->
            called = false
            obj = { x: { current: 1 }, y: { current: 1 }, z: { current: 1 } }
            animator.animate_object_properties(obj, { x: 2, y: 2 }, (() -> called = true), 100)

          it 'should invoke a callback when the animation is complete', ->
            animator.update(1099)
            expect(called).to.be(false)
            animator.update(1100)
            expect(called).to.be(true)

          it 'should return true from needs_update()', ->
            expect(animator.needs_update()).to.be(true)

          it 'should keep needs_update() true after an update that does not finish animation', ->
            animator.update(1099)
            expect(animator.needs_update()).to.be(true)

          it 'should have needs_update() false after the final update', ->
            animator.update(1100)
            expect(animator.needs_update()).to.be(false)

          it 'should not invoke the callback when changing one of the same properties', ->
            animator.animate_object_properties(obj, { y: 3 }, undefined, 600)
            animator.update(1599)
            expect(called).to.be(false)
            animator.update(1600)
            expect(called).to.be(true)

          it 'should invoke the callback when changing some other properties', ->
            animator.animate_object_properties(obj, { z: 3 }, undefined, 600)
            animator.update(1100)
            expect(called).to.be(true)
            expect(obj.z.current).to.eq(2)

          it 'switch to a second animation when needed', ->
            animator.update(600)
            expect(obj.x.current).to.eq(1.5)
            animator.animate_object_properties(obj, { x: 1 }, undefined, 600)
            expect(obj.x.current).to.eq(1.5)
            animator.update(1100)
            expect(obj.x.current).to.eq(1.25)
            animator.update(1600)
            expect(obj.x.current).to.eq(1)

          it 'should cancel the animation when setting new values directly', ->
            animator.update(600)
            animator.set_object_properties(obj, { x: 1 }, undefined, 600)
            expect(obj.x.current).to.eq(1)
            expect(obj.x.start_ms).to.be.undefined
            animator.update(1100) # expect no crashes

          it 'should invoke the callback immediately if setting all its values directly', ->
            animator.set_object_properties(obj, { x: 3, y: 3 })
            expect(called).to.be(true)

          it 'should set needs_update to false when setting new values directly', ->
            animator.set_object_properties(obj, { x: 3, y: 3 })
            expect(animator.needs_update()).to.be(false)

          it 'should not invoke the callback when setting only some animated values directly', ->
            animator.set_object_properties(obj, { x: 1 })
            expect(called).to.be(false)

          it 'should keep needs_update true when setting only some animated values', ->
            animator.set_object_properties(obj, { x: 1 })
            expect(animator.needs_update()).to.be(true)
