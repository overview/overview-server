PropertyInterpolator = require('models/property_interpolator').PropertyInterpolator

EASING = {
  linear: (frac) -> frac,
  quad: (frac) -> frac * frac,
}

describe 'models/property_interpolator', ->
  describe 'PropertyInterpolator', ->
    duration = 1000
    easing = undefined
    interpolator = undefined

    create_interpolator = () ->
      interpolator = new PropertyInterpolator(duration, easing)

    beforeEach ->
      duration = 1000
      easing = undefined

    describe 'with linear interpolator', ->
      beforeEach ->
        easing = EASING.linear
        create_interpolator()

      describe 'update_property()', ->
        it 'should ignore an un-interpolated property', ->
          p = { current: 1 }
          interpolator.update_property(p)
          expect(p).toEqual({ current: 1 })

        it 'should update a property using the easing function', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }
          Timecop.freeze new Date(600), ->
            interpolator.update_property(p)
          expect(p).toEqual({ v1: 1, v2: 2, current: 1.5, start_ms: 100 })

        it 'should update a property given the specified time in ms', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }
          interpolator.update_property(p, 600)
          expect(p).toEqual({ v1: 1, v2: 2, current: 1.5, start_ms: 100 })

        it 'should finalize a property when duration has expired', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }
          interpolator.update_property(p, 1100)
          expect(p).toEqual({ current: 2 })

        it 'should finalize a property when past the duration', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }
          interpolator.update_property(p, 9999)
          expect(p).toEqual({ current: 2 })

        it 'should set to initial value when we go back in time', ->
          # Leap-seconds aren't common, but this is easy to implement so why not do it?
          p = { v1: 1, v2: 2, current: 1, start_ms: 300 }
          interpolator.update_property(p, 200)
          expect(p).toEqual({ v1: 1, v2: 2, current: 1, start_ms: 300 })

        it 'should set to final value if duration is 0 (i.e., animations are off) and tdiff is 0', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }
          interpolator.duration = 0
          interpolator.update_property(p, 100)
          expect(p).toEqual({ current: 2 })

        it 'should set to final value if duration is 0 (i.e., animations are off) and tdiff is >0', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }
          interpolator.duration = 0
          interpolator.update_property(p, 101)
          expect(p).toEqual({ current: 2 })

        it 'should allow a non-linear easing', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 100 }

      describe 'start_time_to_current_eased_fraction', ->
        it 'should convert a time to a fraction', ->
          Timecop.freeze new Date(600), ->
            fraction = interpolator.start_time_to_current_eased_fraction(100)
            expect(fraction).toEqual(0.5)

        it 'should allow specifying the current time as a parameter', ->
          fraction = interpolator.start_time_to_current_eased_fraction(100, 600)
          expect(fraction).toEqual(0.5)

        it 'should convert a time before start to 0', ->
          fraction = interpolator.start_time_to_current_eased_fraction(100, 99)
          expect(fraction).toEqual(0)

        it 'should convert a time after end to 1', ->
          fraction = interpolator.start_time_to_current_eased_fraction(100, 1101)
          expect(fraction).toEqual(1)

        it 'should convert the start time to 1 when @duration=0', ->
          interpolator.duration = 0
          fraction = interpolator.start_time_to_current_eased_fraction(100, 100)
          expect(fraction).toEqual(1)

        it 'should convert a time after the start time to 1 when @duration=0', ->
          interpolator.duration = 0
          fraction = interpolator.start_time_to_current_eased_fraction(100, 101)
          expect(fraction).toEqual(1)

        it 'should use the easing function', ->
          interpolator.easing = EASING.quad
          fraction = interpolator.start_time_to_current_eased_fraction(100, 600)
          expect(fraction).toEqual(0.25)

      describe 'set_property_target', ->
        it 'should change a static property', ->
          p = { current: 1 }
          Timecop.freeze new Date(100), ->
            interpolator.set_property_target(p, 2)
          expect(p).toEqual({ current: 1, v1: 1, v2: 2, start_ms: 100 })

        it 'should change a property given a start time in ms', ->
          p = { current: 1 }
          interpolator.set_property_target(p, 2, 100)
          expect(p).toEqual({ current: 1, v1: 1, v2: 2, start_ms: 100 })

        it 'should change a property mid-interpolation', ->
          p = { v1: 1, v2: 2, current: 1.5, start_ms: 100 }
          interpolator.set_property_target(p, 3, 600)
          expect(p).toEqual({ current: 1.5, v1: 1.5, v2: 3, start_ms: 600 })

        it 'should set the property directly when duration=0', ->
          p = { current: 1 }
          interpolator.duration = 0
          interpolator.set_property_target(p, 2)
          expect(p).toEqual({ current: 2 })

      describe 'update_property_to_fraction', ->
        it 'should exist (see update_property() for more precise tests', ->
          p = { v1: 1, v2: 2, current: 1, start_ms: 1 }
          interpolator.update_property_to_fraction(p, 0.5)
          expect(p).toEqual({ v1: 1, v2: 2, current: 1.5, start_ms: 1 })

        it 'should update arrays', ->
          p = { v1: [ 0, 100, 200 ], v2: [ 200, 100, 0], current: [ 0, 100, 200 ], start_ms: 100 }
          interpolator.update_property_to_fraction(p, 0.25)
          expect(p.current).toEqual([ 50, 100, 150 ])
