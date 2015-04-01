define [
  'apps/Show/models/DocumentList'
], (DocumentList) ->
  describe 'apps/Show/models/DocumentList', ->
    class DocumentSet extends Backbone.Model

    class Tag extends Backbone.Model

    describe 'normally', ->
      beforeEach ->
        @sandbox = sinon.sandbox.create(useFakeServer: true)
        @documentSet = new DocumentSet()
        @params =
          documentSet: @documentSet
          toQueryParams: -> { tags: '2' }
          equals: -> true
          reset:
            byDocument: (document) ->
              documentSet: @documentSet
              document: document
              equals: -> false

        @list = new DocumentList({}, {
          params: @params
          url: '/documentsets/1/documents'
        })
        @docs = @list.documents

      afterEach ->
        @documentSet.off()
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
            @documentSet.trigger('tag', tag, @params.toQueryParams())
            expect(@docs.at(0).hasTag(tag)).to.be.true
            expect(@docs.at(1).hasTag(tag)).to.be.true

          it 'should untag the list, client-side', ->
            tag = new Tag(name: 'a tag')
            @docs.at(0).tag(tag)
            @documentSet.trigger('untag', tag, @params.toQueryParams())
            expect(@docs.at(0).hasTag(tag)).to.be.false
            expect(@docs.at(1).hasTag(tag)).to.be.false

          it 'should tag a document, client-side', ->
            tag = new Tag(name: 'a tag')
            @docs.at(0).untag(tag)
            @docs.at(1).untag(tag)
            @documentSet.trigger('tag', tag, documents: String(@docs.at(0).id))
            expect(@docs.at(0).hasTag(tag)).to.be.true
            expect(@docs.at(1).hasTag(tag)).to.be.false

          it 'should untag a document, client-side', ->
            tag = new Tag(name: 'a tag')
            @docs.at(0).tag(tag)
            @docs.at(1).tag(tag)
            @documentSet.trigger('untag', tag, documents: String(@docs.at(0).id))
            expect(@docs.at(0).hasTag(tag)).to.be.false
            expect(@docs.at(1).hasTag(tag)).to.be.true

          it 'should tag two documents with multi-digit IDs', ->
            # JavaScript idiosyncracy: a simple for-loop over "123" will
            # iterate over "1", "2" and "3".
            tag = new Tag(name: 'a tag')
            @docs.add([
              { id: 123 }
              { id: 234 }
              { id: 345 }
            ])
            @documentSet.trigger('tag', tag, documents: '123,345')
            expect(@docs.get(123).hasTag(tag)).to.be.true
            expect(@docs.get(234).hasTag(tag)).to.be.false
            expect(@docs.get(345).hasTag(tag)).to.be.true

          it 'should untag two documents with multi-digit IDs', ->
            tag = new Tag(name: 'a tag')
            @docs.add([
              { id: 123 }
              { id: 234 }
              { id: 345 }
            ])
            @docs.get(123).tag(tag)
            @docs.get(234).tag(tag)
            @docs.get(345).tag(tag)
            @documentSet.trigger('untag', tag, documents: '123,345')
            expect(@docs.get(123).hasTag(tag)).to.be.false
            expect(@docs.get(234).hasTag(tag)).to.be.true
            expect(@docs.get(345).hasTag(tag)).to.be.false

          it 'should trigger nothing when tagging or untagging a single document', ->
            tag = new Tag(name: 'a tag')
            @list.on('all', spy = sinon.spy())
            @documentSet.trigger('tag', tag, documents: String(@docs.at(0).id))
            @documentSet.trigger('untag', tag, documents: String(@docs.at(0).id))
            expect(spy).not.to.have.been.called

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
