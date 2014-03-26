define [
  'apps/Tree/models/log'
], (Log) ->
  create_entry = (options={}) ->
    {
      component: options.component || 'a component'
      action: options.action || 'an action'
      details: options.details || 'some details'
    }

  describe 'models/log', ->
    beforeEach ->
      spyOn($, 'ajax')

    describe 'Log', ->
      describe 'entries', ->
        it 'should begin empty', ->
          log = new Log()
          expect(log.entries).toEqual([])

      describe 'add_entry', ->
        it 'should add entries', ->
          log = new Log()
          log.add_entry(create_entry())
          expect(log.entries.length).toEqual(1)

        it 'should add time to the entry as a string', ->
          log = new Log()
          Timecop.freeze new Date(Date.UTC(2012, 6, 4, 15, 36, 25, 0)), ->
            log.add_entry(create_entry())
          expect(log.entries[0].date).toEqual("2012-07-04T15:36:25.000Z")

        it 'should behave as pass-by-value', ->
          # In other words, the Log will hold a "date" in its object; but the
          # caller won't see any changes to the argument it passed.
          log = new Log()
          entry = create_entry()
          same_entry = create_entry()
          log.add_entry(entry)
          expect(entry).toEqual(same_entry)

        it 'should set empty values for component and action and details', ->
          log = new Log()
          log.add_entry({})
          entry = log.entries[0]
          expect(entry.component).toEqual('')
          expect(entry.action).toEqual('')
          expect(entry.details).toEqual('')

      describe 'clear_entries', ->
        it 'should clear entries', ->
          log = new Log()
          log.add_entry(create_entry())
          log.clear_entries()
          expect(log.entries).toEqual([])

      describe 'upload_entries_to_server_and_clear', ->
        log = undefined

        beforeEach ->
          log = new Log()
          log.add_entry(create_entry())

        it 'should clear entries', ->
          log.upload_entries_to_server_and_clear()
          expect(log.entries).toEqual([])

        it 'should send an ajax request', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax).toHaveBeenCalled()

        it 'should post to the proper path', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax.calls.mostRecent().args[0].url).toMatch(/\bcreate-many\b/)

        it 'should post a JSON array', ->
          entry = log.entries[0]
          log.upload_entries_to_server_and_clear()
          data = $.ajax.calls.mostRecent().args[0].data
          expect(data).toMatch(/^\[\{.*\}\]$/)
          expect(data).toMatch(new RegExp("\"component\":\s*\"#{entry.component}\""))

        it 'should not post when empty', ->
          log.clear_entries()
          log.upload_entries_to_server_and_clear()
          expect($.ajax).not.toHaveBeenCalled()

        it 'should set contentType: "application/json" on the $.ajax request', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax.calls.mostRecent().args[0].contentType).toEqual('application/json')

        it 'should set global: false on the $.ajax request', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax.calls.mostRecent().args[0].global).toBe(false)

      describe 'for_component()', ->
        it 'should return a function that can be used as a shortcut', ->
          log = new Log()
          spyOn(log, 'add_entry').and.callThrough()
          log_shortcut = log.for_component('test')
          log_shortcut('action', 'details')
          expect(log.add_entry).toHaveBeenCalledWith({ component: 'test', action: 'action', details: 'details' })
