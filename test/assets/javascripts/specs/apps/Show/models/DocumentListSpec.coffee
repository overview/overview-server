define [
  'apps/Show/models/DocumentList'
], (DocumentList) ->
  describe 'apps/Show/models/DocumentList', ->
    class Tag extends Backbone.Model
      addToDocumentsOnServer: ->
      removeFromDocumentsOnServer: ->

    describe 'normally', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true)

        @params =
          toQueryParams: -> { tags: '2' }

        @list = new DocumentList({}, {
          params: @params
          url: '/documentsets/1/documents'
        })

        @docs = @list.documents

      afterEach ->
        @list.stopListening()
        @list.off()
        @docs.off()
        @sandbox.restore()

      it 'should set params', -> expect(@list.params).to.eq(@params)
      it 'should be empty', -> expect(@docs.length).to.eq(0)
      it 'should start with length=null', -> expect(@list.get('length')).to.be.null
      it 'should start with loading=false', -> expect(@list.get('loading')).to.be.false
      it 'should have nDocumentsPerPage=20', -> expect(@list.nDocumentsPerPage).to.eq(20)
      it 'should have nPagesFetched=0', -> expect(@list.get('nPagesFetched')).to.eq(0)

      describe 'on first .fetchNextPage()', ->
        beforeEach ->
          @promise1 = @list.fetchNextPage()
          undefined

        it 'should have length=null', -> expect(@list.get('length')).to.be.null
        it 'should have no documents', -> expect(@docs.length).to.eq(0)
        it 'should have loading=true', -> expect(@list.get('loading')).to.be.true
        it 'should have nPagesFetched=0', -> expect(@list.get('nPagesFetched')).to.eq(0)
        it 'should have isComplete=false', -> expect(@list.isComplete()).to.be.false
        it 'should request /documents', ->
          expect(@sandbox.server.requests.length).to.eq(1)
          req = @sandbox.server.requests[0]
          expect(req.method).to.eq('GET')
          expect(req.url).to.eq('/documentsets/1/documents?tags=2&limit=20&offset=0')
        it 'should return the same promise and not change anything when calling again', ->
          p1 = @list.fetchNextPage()
          p2 = @list.fetchNextPage()
          expect(p1).to.eq(p2)
          expect(@docs.length).to.eq(0)

        describe 'on error', ->
          beforeEach ->
            @sandbox.server.requests[0].respond(400, {'Content-Type': 'application/json'}, '{"message":"error message"}')

          it 'should set loading=false', -> expect(@list.get('loading')).to.be.false
          it 'should set statusCode', -> expect(@list.get('statusCode')).to.eq(400)
          it 'should set error', -> expect(@list.get('error')).to.eq('error message')

        describe 'on zero-doc success', ->
          beforeEach ->
            @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(
              documents: []
              total_items: 0
            ))
            @promise1 # mocha-as-promised

          it 'should set length', -> expect(@list.get('length')).to.eq(0)
          it 'should set loading=false', -> expect(@list.get('loading')).to.be.false
          it 'should have nPagesFetched=1', -> expect(@list.get('nPagesFetched')).to.eq(1)
          it 'should have isComplete=true', -> expect(@list.isComplete()).to.be.true
          it 'should return a resolved promise on fetchNextPage()', -> expect(@list.fetchNextPage()).to.be.fulfilled

        describe 'on a-few-docs success', ->
          beforeEach ->
            @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(
              documents: [ { id: 1 }, { id: 2 }, { id: 3 } ]
              total_items: 3
            ))
            @promise1 # mocha-as-promised

          it 'should populate with the documents', -> expect(@docs.pluck('id')).to.deep.eq([ 1, 2, 3 ])
          it 'should set length', -> expect(@list.get('length')).to.eq(3)
          it 'should set loading=false', -> expect(@list.get('loading')).to.be.false
          it 'should have nPagesFetched=1', -> expect(@list.get('nPagesFetched')).to.eq(1)
          it 'should have isComplete=true', -> expect(@list.isComplete()).to.be.true

        describe 'on one-page success', ->
          beforeEach ->
            @sandbox.server.requests[0].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(
              documents: ({ id: x } for x in [ 1 .. @list.nDocumentsPerPage ])
              total_items: @list.nDocumentsPerPage + 1
            ))
            @promise1 # mocha-as-promised

          it 'should populate with the documents', -> expect(@docs.length).to.eq(@list.nDocumentsPerPage)
          it 'should have loading=false', -> expect(@list.get('loading')).to.be.false
          it 'should set length', -> expect(@list.get('length')).to.eq(@list.nDocumentsPerPage + 1)
          it 'should have isComplete=false', -> expect(@list.isComplete()).to.be.false

          it 'should tag the list, client-side', ->
            tag = new Tag(name: 'a tag')
            @list.tagLocal(tag)
            expect(@docs.at(0).hasTag(tag)).to.be.true
            expect(@docs.at(1).hasTag(tag)).to.be.true

          it 'should untag the list, client-side', ->
            tag = new Tag(name: 'a tag')
            @docs.at(0).tag(tag)
            @list.untagLocal(tag)
            expect(@docs.at(0).hasTag(tag)).to.be.false
            expect(@docs.at(1).hasTag(tag)).to.be.false

          it 'should tag the list, server-side', ->
            tag = new Tag(name: 'a tag')
            @sandbox.stub(tag, 'addToDocumentsOnServer')
            @list.tag(tag)
            expect(@docs.at(0).hasTag(tag)).to.be.true
            expect(tag.addToDocumentsOnServer).to.have.been.calledWith(tags: '2')

          it 'should untag the list, server-side', ->
            tag = new Tag(name: 'a tag')
            @sandbox.stub(tag, 'removeFromDocumentsOnServer')
            @docs.at(0).tagLocal(tag)
            @list.untag(tag)
            expect(@docs.at(0).hasTag(tag)).to.be.false
            expect(tag.removeFromDocumentsOnServer).to.have.been.calledWith(tags: '2')

          describe 'on subsequent fetchNextPage()', ->
            beforeEach ->
              @promise2 = @list.fetchNextPage()
              undefined # mocha-as-promised

            it 'should have nPagesFetched=1', -> expect(@list.get('nPagesFetched')).to.eq(1)
            it 'should have loading=true', -> expect(@list.get('loading')).to.be.true

            it 'should send a new request', ->
              expect(@sandbox.server.requests.length).to.eq(2)
              req = @sandbox.server.requests[1]
              expect(req.method).to.eq('GET')
              expect(req.url).to.eq('/documentsets/1/documents?tags=2&limit=20&offset=20')

            it 'should apply tags to the resulting documents', ->
              tag = new Tag(name: 'a tag')
              @list.tagLocal(tag)
              @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(
                documents: [ { id: @list.nDocumentsPerPage + 1 } ]
                total_items: @list.nDocumentsPerPage + 1
              ))
              @promise2.then =>
                expect(@docs.at(@list.nDocumentsPerPage).hasTag(tag)).to.be.true

            it 'should unapply tags from the resulting documents', ->
              tag = new Tag(id: 1, name: 'a tag')
              @list.untagLocal(tag)
              @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(
                documents: [ { id: @list.nDocumentsPerPage + 1, tagids: [ 1 ] } ]
                total_items: @list.nDocumentsPerPage + 1
              ))
              @promise2.then =>
                expect(@docs.at(@list.nDocumentsPerPage).hasTag(tag)).to.be.false

            describe 'on success', ->
              beforeEach ->
                @sandbox.server.requests[1].respond(200, { 'Content-Type': 'application/json' }, JSON.stringify(
                  documents: [ { id: @list.nDocumentsPerPage + 1 } ]
                  total_items: @list.nDocumentsPerPage + 1
                ))
                @promise2

              it 'should have nPagesFetched=2', -> expect(@list.get('nPagesFetched')).to.eq(2)
              it 'should have loading=false', -> expect(@list.get('loading')).to.be.false
              it 'should have isComplete=true', -> expect(@list.isComplete()).to.be.true
              it 'should have all documents', -> expect(@docs.pluck('id')).to.deep.eq([ 1 .. (@list.nDocumentsPerPage + 1) ])
              it 'should return a resolved promise on fetchNextPage()', -> expect(@list.fetchNextPage()).to.be.fulfilled
