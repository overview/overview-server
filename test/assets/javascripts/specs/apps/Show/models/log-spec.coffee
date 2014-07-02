define [
  'apps/Show/models/log'
], (Log) ->
  create_entry = (options={}) ->
    {
      component: options.component || 'a component'
      action: options.action || 'an action'
      details: options.details || 'some details'
    }

  describe 'models/log', ->
    beforeEach ->
      @sandbox = sinon.sandbox.create()
      @sandbox.stub($, 'ajax')

    afterEach ->
      @sandbox.restore()

    describe 'Log', ->
      describe 'entries', ->
        it 'should begin empty', ->
          log = new Log()
          expect(log.entries).to.deep.eq([])

      describe 'add_entry', ->
        it 'should add entries', ->
          log = new Log()
          log.add_entry(create_entry())
          expect(log.entries.length).to.eq(1)

        it 'should add time to the entry as a string', ->
          @sandbox.useFakeTimers() # so clock stays put
          log = new Log()
          log.add_entry(create_entry())
          expect(log.entries[0].date).to.eq(new Date().toISOString())

        it 'should behave as pass-by-value', ->
          # In other words, the Log will hold a "date" in its object; but the
          # caller won't see any changes to the argument it passed.
          log = new Log()
          entry = create_entry()
          same_entry = create_entry()
          log.add_entry(entry)
          expect(entry).to.deep.eq(same_entry)

        it 'should set empty values for component and action and details', ->
          log = new Log()
          log.add_entry({})
          entry = log.entries[0]
          expect(entry.component).to.eq('')
          expect(entry.action).to.eq('')
          expect(entry.details).to.eq('')

      describe 'clear_entries', ->
        it 'should clear entries', ->
          log = new Log()
          log.add_entry(create_entry())
          log.clear_entries()
          expect(log.entries).to.deep.eq([])

      describe 'upload_entries_to_server_and_clear', ->
        log = undefined

        beforeEach ->
          log = new Log()
          log.add_entry(create_entry())

        it 'should clear entries', ->
          log.upload_entries_to_server_and_clear()
          expect(log.entries).to.deep.eq([])

        it 'should send an ajax request', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax).to.have.been.called

        it 'should post to the proper path', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax.lastCall.args[0].url).to.match(/\bcreate-many\b/)

        it 'should post a JSON array', ->
          entry = log.entries[0]
          log.upload_entries_to_server_and_clear()
          data = $.ajax.lastCall.args[0].data
          expect(data).to.match(/^\[\{.*\}\]$/)
          expect(data).to.match(new RegExp("\"component\":\s*\"#{entry.component}\""))

        it 'should not post when empty', ->
          log.clear_entries()
          log.upload_entries_to_server_and_clear()
          expect($.ajax).not.to.have.been.called

        it 'should set contentType: "application/json" on the $.ajax request', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax.lastCall.args[0].contentType).to.eq('application/json')

        it 'should set global: false on the $.ajax request', ->
          log.upload_entries_to_server_and_clear()
          expect($.ajax.lastCall.args[0].global).to.be.false

      describe 'for_component()', ->
        it 'should return a function that can be used as a shortcut', ->
          log = new Log()
          @sandbox.spy(log, 'add_entry')
          log_shortcut = log.for_component('test')
          log_shortcut('action', 'details')
          expect(log.add_entry).to.have.been.calledWith({ component: 'test', action: 'action', details: 'details' })
