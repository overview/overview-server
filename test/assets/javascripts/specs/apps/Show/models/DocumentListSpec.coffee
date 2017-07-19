define [
  'apps/Show/models/DocumentList'
], (DocumentList) ->
  describe 'apps/Show/models/DocumentList', ->
    class Tag extends Backbone.Model
      addToDocumentsOnServer: ->
      removeFromDocumentsOnServer: ->

    beforeEach ->
      @sandbox = sinon.sandbox.create(useFakeServer: true)

      @documentSet = new Backbone.Model(id: 1)
      @documentSet.url = '/documentsets/1'

      @transactionQueue = {}

      @params =
        toJSON: -> { tags: [ 20 ] }
        toQueryString: -> 'tags=20'

      @list = new DocumentList({}, documentSet: @documentSet, transactionQueue: @transactionQueue, params: @params)

      @tagCountsChangedSpy = sinon.spy()
      @list.on('tag-counts-changed', @tagCountsChangedSpy)

      @docs = @list.documents

    afterEach ->
      @list.stopListening()
      @list.off()
      @docs.off()
      @sandbox.restore()

    it 'should set documentSet', -> expect(@list.documentSet).to.eq(@documentSet)
    it 'should set params', -> expect(@list.params).to.eq(@params)
    it 'should be empty', -> expect(@docs.length).to.eq(0)
    it 'should start with length=null', -> expect(@list.get('length')).to.be.null
    it 'should start with loading=false', -> expect(@list.get('loading')).to.be.false
    it 'should have nDocumentsPerPage=20', -> expect(@list.nDocumentsPerPage).to.eq(20)
    it 'should have nPagesFetched=0', -> expect(@list.get('nPagesFetched')).to.eq(0)
    it 'should have tagCount=0,atLeast', -> expect(@list.getTagCount(new Tag())).to.deep.eq(n: 0, howSure: 'atLeast')
    it 'should have selectionId=null', -> expect(@list.get('selectionId')).to.be.null
    it 'should have warnings=[]', -> expect(@list.get('warnings')).to.deep.eq([])

    it 'should have tagCount=0,exact when not-the-tag is the params', ->
      tag = new Tag(name: 'a tag')
      # Violate API and modify params: there's no way in these unit tests
      # to create a new DocumentList.
      @list.params = { toJSON: -> tags: [ tag.id ], tagOperation: 'none' }
      expect(@list.getTagCount(tag)).to.deep.eq(n: 0, howSure: 'exact')

    it 'should add &reverse=true to URL when given to constructor', ->
      # Note: reverse isn't an attribute, since it's only set on the constructor.
      @list = new DocumentList({}, documentSet: @documentSet, transactionQueue: @transactionQueue, params: @params, reverse: true)
      @transactionQueue.streamJsonArray = sinon.stub().returns(Promise.resolve(null))
      @list.fetchNextPage()

      expect(@transactionQueue.streamJsonArray).to.have.been.calledWithMatch({
        url: '/documentsets/1/documents?tags=20&refresh=true&limit=20&offset=0&reverse=true'
      })

    describe 'on first .fetchNextPage()', ->
      beforeEach ->
        @ajaxPromise1 = new Promise (resolve, reject) =>
          @ajaxResolve1 = resolve
          @ajaxReject1 = reject
        @transactionQueue.streamJsonArray = sinon.stub().returns(@ajaxPromise1)
        @promise1 = @list.fetchNextPage()
        undefined

      it 'should have length=null', -> expect(@list.get('length')).to.be.null
      it 'should have no documents', -> expect(@docs.length).to.eq(0)
      it 'should have loading=true', -> expect(@list.get('loading')).to.be.true
      it 'should have nPagesFetched=0', -> expect(@list.get('nPagesFetched')).to.eq(0)
      it 'should have isComplete=false', -> expect(@list.isComplete()).to.be.false
      it 'should have selectionId=null', -> expect(@list.get('selectionId')).to.be.null
      it 'should have warnings=[]', -> expect(@list.get('warnings')).to.deep.eq([])

      it 'should request /documents', ->
        expect(@transactionQueue.streamJsonArray).to.have.been.calledWithMatch({
          url: '/documentsets/1/documents?tags=20&refresh=true&limit=20&offset=0'
        })

      it 'should return the same promise and not change anything when calling again', ->
        p1 = @list.fetchNextPage()
        p2 = @list.fetchNextPage()
        expect(p1).to.eq(p2)
        expect(@docs.length).to.eq(0)
        expect(@transactionQueue.streamJsonArray).to.have.been.called.once

      it 'should have tagCount=n,exact if we tagged before the page loaded', ->
        tag = new Tag(id: 1)
        @list.tagLocal(tag)
        expect(@tagCountsChangedSpy).to.have.been.called.once

        @transactionQueue.streamJsonArray.args[0][0].onItem(
          documents: ({ id: x } for x in [ 1 .. @list.nDocumentsPerPage ])
          total_items: @list.nDocumentsPerPage + 1
          selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
        )
        @ajaxResolve1()

        @promise1.then =>
          expect(@list.getTagCount(tag)).to.deep.eq(n: @list.nDocumentsPerPage + 1, howSure: 'exact')
          expect(@tagCountsChangedSpy).to.have.been.called.twice

      it 'should set progress when streaming', ->
        @progressChangedSpy = sinon.spy()
        @list.on('change:progress', @progressChangedSpy)
        @transactionQueue.streamJsonArray.args[0][0].onItem(progress: 0.3)
        @transactionQueue.streamJsonArray.args[0][0].onItem(progress: 0.6)
        @transactionQueue.streamJsonArray.args[0][0].onItem(
          documents: ({ id: x } for x in [ 1 .. @list.nDocumentsPerPage ])
          total_items: @list.nDocumentsPerPage + 1
          selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
        )
        @ajaxResolve1()

        @promise1.then =>
          expect(@progressChangedSpy.args.map((a) => a[1])).to.deep.eq([ 0.3, 0.6, null ])

      describe 'on error', ->
        beforeEach ->
          @originalErrorHandler = sinon.spy()
          @ajaxReject1({
            thrown: new Error('error message')
            statusCode: 400
          })
          @promise1.catch((e) =>)

        it 'should set loading=false', -> expect(@list.get('loading')).to.be.false
        it 'should set progress=null', -> expect(@list.get('progress')).to.be.null
        it 'should set statusCode', -> expect(@list.get('statusCode')).to.eq(400)
        it 'should set error', -> expect(@list.get('error')).to.eq('error message')
        it 'should have tagCount=0(atLeast)', -> expect(@list.getTagCount(new Tag())).to.deep.eq(n: 0, howSure: 'atLeast')

      describe 'on zero-doc success', ->
        beforeEach ->
          @transactionQueue.streamJsonArray.args[0][0].onItem(
            documents: []
            total_items: 0
            selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
          )
          @ajaxResolve1()
          @promise1 # mocha-as-promised

        it 'should set length', -> expect(@list.get('length')).to.eq(0)
        it 'should set loading=false', -> expect(@list.get('loading')).to.be.false
        it 'should have nPagesFetched=1', -> expect(@list.get('nPagesFetched')).to.eq(1)
        it 'should have isComplete=true', -> expect(@list.isComplete()).to.be.true
        it 'should return a resolved promise on fetchNextPage()', -> expect(@list.fetchNextPage()).to.be.fulfilled
        it 'should have tagCount=0(exact)', -> expect(@list.getTagCount(new Tag())).to.deep.eq(n: 0, howSure: 'exact')

      describe 'on a-few-docs success', ->
        beforeEach ->
          @tag = new Tag(id: 5)
          @transactionQueue.streamJsonArray.args[0][0].onItem(
            documents: [
              { documentSetId: 1, id: 1, tagids: [ 5 ] }
              { documentSetId: 1, id: 2, tagids: [ 5 ] }
              { documentSetId: 1, id: 3 }
            ]
            warnings: [
              { type: 'TooManyExpansions', field: 'text', term: 'foo*', nExpansions: 1000 }
            ]
            total_items: 3
            selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
          )
          @ajaxResolve1()
          @promise1 # mocha-as-promised

        it 'should populate with the documents', -> expect(@docs.pluck('id')).to.deep.eq([ 1, 2, 3 ])
        it 'should set length', -> expect(@list.get('length')).to.eq(3)
        it 'should set loading=false', -> expect(@list.get('loading')).to.be.false
        it 'should have nPagesFetched=1', -> expect(@list.get('nPagesFetched')).to.eq(1)
        it 'should have isComplete=true', -> expect(@list.isComplete()).to.be.true
        it 'should have tagCount=n,exact', -> expect(@list.getTagCount(@tag)).to.deep.eq(n: 2, howSure: 'exact')
        it 'should fire tag-counts-changed', -> expect(@tagCountsChangedSpy).to.have.been.called.once
        it 'should set warnings=[]', -> expect(@list.get('warnings')).to.deep.eq([{ type: 'TooManyExpansions', field: 'text', term: 'foo*', nExpansions: 1000 } ])

        it 'should have the right url on every document', ->
          expect(@docs.at(1).url()).to.eq('/documentsets/1/documents/2')

        it 'should increase tag count when a document is tagged', ->
          @docs.at(2).tagLocal(@tag)
          expect(@list.getTagCount(@tag)).to.deep.eq(n: 3, howSure: 'exact')
          expect(@tagCountsChangedSpy).to.have.been.called.twice

        it 'should decrease tag count when a document is untagged', ->
          @docs.at(1).untagLocal(@tag)
          expect(@list.getTagCount(@tag)).to.deep.eq(n: 1, howSure: 'exact')
          expect(@tagCountsChangedSpy).to.have.been.called.twice

      describe 'on one-page success', ->
        beforeEach ->
          @transactionQueue.streamJsonArray.args[0][0].onItem(
            documents: ({ id: x, tagids: [ 1+x%2 ] } for x in [ 1 .. @list.nDocumentsPerPage ])
            total_items: @list.nDocumentsPerPage + 1
            selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
          )
          @ajaxResolve1()
          @promise1 # mocha-as-promised

        it 'should populate with the documents', -> expect(@docs.length).to.eq(@list.nDocumentsPerPage)
        it 'should have loading=false', -> expect(@list.get('loading')).to.be.false
        it 'should set length', -> expect(@list.get('length')).to.eq(@list.nDocumentsPerPage + 1)
        it 'should have isComplete=false', -> expect(@list.isComplete()).to.be.false
        it 'should have tagCount=0,atLeast', -> expect(@list.getTagCount(new Tag())).to.deep.eq(n: 0, howSure: 'atLeast')
        it 'should have selectionId', -> expect(@list.get('selectionId')).to.eq('ea21b9a6-4f4b-42f9-a694-f177eba71ed7')

        it 'should have tagCount=n,atLeast when computed from the server', ->
          tag = new Tag(id: 2)
          expect(@list.getTagCount(tag)).to.deep.eq(n: @list.nDocumentsPerPage>>1, howSure: 'atLeast')

        it 'should have tagCount=n,atLeast when lazy-computed after client-side changes', ->
          tag = new Tag(id: 2)
          @docs.at(1).tagLocal(tag)
          expect(@list.getTagCount(tag)).to.deep.eq(n: (@list.nDocumentsPerPage>>1) + 1, howSure: 'atLeast')

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
          expect(tag.addToDocumentsOnServer).to.have.been.calledWith(selectionId: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7')

        it 'should untag the list, server-side', ->
          tag = new Tag(name: 'a tag')
          @sandbox.stub(tag, 'removeFromDocumentsOnServer')
          @docs.at(0).tagLocal(tag)
          @list.untag(tag)
          expect(@docs.at(0).hasTag(tag)).to.be.false
          expect(tag.removeFromDocumentsOnServer).to.have.been.calledWith(selectionId: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7')

        it 'should set tagCount=n,exact when tagging the list', ->
          tag = new Tag(name: 'a tag')
          @list.tagLocal(tag)
          expect(@list.getTagCount(tag)).to.deep.eq(n: @list.get('length'), howSure: 'exact')
          expect(@tagCountsChangedSpy).to.have.been.called.twice

        it 'should set tagCount=0,exact when untagging the list', ->
          tag = new Tag(name: 'a tag')
          @list.untagLocal(tag)
          expect(@list.getTagCount(tag)).to.deep.eq(n: 0, howSure: 'exact')
          expect(@tagCountsChangedSpy).to.have.been.called.twice

        it 'should give tagCount=n,exact when the tag is the params', ->
          expect(@list.getTagCount(new Tag(id: 20))).to.deep.eq(n: @list.get('length'), howSure: 'exact')

        describe 'on subsequent fetchNextPage()', ->
          beforeEach ->
            @ajaxPromise2 = new Promise (resolve, reject) =>
              @ajaxResolve2 = resolve
              @ajaxReject2 = reject
            @transactionQueue.streamJsonArray.returns(@ajaxPromise2)
            @promise2 = @list.fetchNextPage()
            undefined # mocha-as-promised

          it 'should have nPagesFetched=1', -> expect(@list.get('nPagesFetched')).to.eq(1)
          it 'should have loading=true', -> expect(@list.get('loading')).to.be.true

          it 'should send a new request with the selectionId', ->
            expect(@transactionQueue.streamJsonArray).to.have.been.calledWithMatch({
              url: '/documentsets/1/documents?selectionId=ea21b9a6-4f4b-42f9-a694-f177eba71ed7&limit=20&offset=20'
            })

          it 'should keep progress==null', ->
            @onProgressChanged = sinon.spy()
            @list.on('change:progress', @onProgressChanged)
            @transactionQueue.streamJsonArray.args[1][0].onItem(
              { progress: 0.1 },
              {
                documents: [ { id: @list.nDocumentsPerPage + 1 } ]
                total_items: @list.nDocumentsPerPage + 1
                selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
              }
            )
            @ajaxResolve2()
            @promise2.then =>
              expect(@onProgressChanged).not.to.have.been.called

          it 'should apply tags to the resulting documents', ->
            tag = new Tag(name: 'a tag')
            @list.tagLocal(tag)
            @transactionQueue.streamJsonArray.args[1][0].onItem(
              documents: [ { id: @list.nDocumentsPerPage + 1 } ]
              total_items: @list.nDocumentsPerPage + 1
              selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
            )
            @ajaxResolve2()
            @promise2.then =>
              expect(@docs.at(@list.nDocumentsPerPage).hasTag(tag)).to.be.true
              expect(@list.getTagCount(tag)).to.deep.eq(n: @list.nDocumentsPerPage + 1, howSure: 'exact')

          it 'should unapply tags from the resulting documents', ->
            tag = new Tag(id: 1, name: 'a tag')
            @list.untagLocal(tag)
            @transactionQueue.streamJsonArray.args[1][0].onItem(
              documents: [ { id: @list.nDocumentsPerPage + 1 } ]
              total_items: @list.nDocumentsPerPage + 1
              selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
            )
            @ajaxResolve2()
            @promise2.then =>
              expect(@docs.at(@list.nDocumentsPerPage).hasTag(tag)).to.be.false
              expect(@list.getTagCount(tag)).to.deep.eq(n: 0, howSure: 'exact')

          it 'should set tagCount=n,exact when fetching the final page', ->
            tag = new Tag(id: 1, name: 'a tag')
            expect(@list.getTagCount(tag)).to.deep.eq(n: @list.nDocumentsPerPage>>1, howSure: 'atLeast') # Precondition

            @transactionQueue.streamJsonArray.args[1][0].onItem(
              documents: [ { id: @list.nDocumentsPerPage + 1, tagids: [ 1 ] } ]
              total_items: @list.nDocumentsPerPage + 1
              selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
            )
            @ajaxResolve2()
            @promise2.then =>
              expect(@list.getTagCount(tag)).to.deep.eq(n: (@list.nDocumentsPerPage>>1) + 1, howSure: 'exact')

          describe 'on success', ->
            beforeEach ->
              @transactionQueue.streamJsonArray.args[1][0].onItem(
                documents: [ { id: @list.nDocumentsPerPage + 1 } ]
                total_items: @list.nDocumentsPerPage + 1
                selection_id: 'ea21b9a6-4f4b-42f9-a694-f177eba71ed7'
              )
              @ajaxResolve2()
              @promise2

            it 'should have nPagesFetched=2', -> expect(@list.get('nPagesFetched')).to.eq(2)
            it 'should have loading=false', -> expect(@list.get('loading')).to.be.false
            it 'should have isComplete=true', -> expect(@list.isComplete()).to.be.true
            it 'should have all documents', -> expect(@docs.pluck('id')).to.deep.eq([ 1 .. (@list.nDocumentsPerPage + 1) ])
            it 'should return a resolved promise on fetchNextPage()', -> expect(@list.fetchNextPage()).to.be.fulfilled
