Log = require('models/log').Log

create_entry = (options={}) ->
  {
    component: options.component || 'a component'
    action: options.action || 'an action'
    details: options.details || 'some details'
  }

describe 'models/log', ->
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

      it 'should add time to the entry, as a string', ->
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

      it 'should set empty values for component, action and details', ->
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
      class MockServer
        post: (@path, @data, @options) ->

      log = undefined
      mock_server = undefined

      beforeEach ->
        log = new Log()
        log.add_entry(create_entry())
        mock_server = new MockServer()

      it 'should clear entries', ->
        log.upload_entries_to_server_and_clear(mock_server)
        expect(log.entries).toEqual([])

      it 'should post to the proper path', ->
        log.upload_entries_to_server_and_clear(mock_server)
        expect(mock_server.path).toEqual('create_log_entries')

      it 'should post a JSON array', ->
        entry = log.entries[0]
        log.upload_entries_to_server_and_clear(mock_server)
        expect(mock_server.data).toMatch(/^\[\{.*\}\]$/)
        expect(mock_server.data).toMatch(new RegExp("\"component\":\s*\"#{entry.component}\""))

      it 'should not post when empty', ->
        log.clear_entries()
        log.upload_entries_to_server_and_clear(mock_server)
        expect(mock_server.path).toBeUndefined()
