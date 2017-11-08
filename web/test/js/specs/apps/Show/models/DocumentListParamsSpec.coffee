define [
  'underscore'
  'backbone'
  'apps/Show/models/DocumentListParams'
], (_, Backbone, DocumentListParams) ->
  describe 'apps/Show/models/DocumentListParams', ->
    describe 'normalize', ->
      n = DocumentListParams.normalize

      it 'should parse q', -> expect(n(q: 'foo')).to.have.property('q', 'foo')
      it 'should not parse empty q', -> expect(n(q: ' ')).not.to.have.property('q')
      it 'should parse tag IDs', -> expect(n(tags: [ 1, 2 ]).tags).to.deep.eq(ids: [ 1, 2 ])
      it 'should parse tag IDs long-form', -> expect(n(tags: { ids: [ 1, 2 ] }).tags).to.deep.eq(ids: [ 1, 2 ])
      it 'should parse untagged', -> expect(n(tags: { tagged: false }).tags).to.deep.eq(tagged: false)
      it 'should set q=undefined by default', -> expect(n({}).q).not.to.be.defined
      it 'should set tags=undefined by default', -> expect(n({}).tags).not.to.be.defined
      it 'should set objects=undefined by default', -> expect(n({}).objects).not.to.be.defined
      it 'should set sortByMetadataField=undefined by default', -> expect(n({}).sortByMetadataField).not.to.be.defined

      it 'should parse tag NONE op', ->
        expect(n(tags: { ids: [1], tagged: false, operation: 'none' }).tags)
          .to.deep.eq(ids: [1], tagged: false, operation: 'none')

      it 'should parse tag ALL op', ->
        expect(n(tags: { ids: [ 1, 2 ], operation: 'all' }).tags)
          .to.deep.eq(ids: [ 1, 2 ], operation: 'all')

      it 'should parse tag ANY op and omit it, because any is the default', ->
        expect(n(tags: { ids: [ 1, 2 ], operation: 'any' }).tags).to.deep.eq(ids: [ 1, 2 ])

      it 'should error when given a bad tag op', ->
        expect(-> n(tags: { ids: [ 1, 2 ], operation: 'foo' }))
          .to.throw('Invalid option tags.operation="foo"')

      it 'should error when given a bad tagged value', ->
        expect(-> n(tags: { tagged: 3 })).to.throw('Invalid option tags.tagged=3')

      it 'should error when given a bad tag key', ->
        expect(-> n(tags: { foo: 'bar' })).to.throw('Invalid option tags.foo')

      it 'should parse an object spec with ids', ->
        expect(n(objects: { ids: [ 1, 2 ], title: '%s in foo' }).objects)
          .to.deep.eq(ids: [ 1, 2 ], title: '%s in foo')

      it 'should parse an object spec with nodeIds', ->
        expect(n(objects: { nodeIds: [ 1, 2 ], title: '%s in foo' }).objects)
          .to.deep.eq(nodeIds: [ 1, 2 ], title: '%s in foo')

      it 'should parse an object spec with sortByMetadataField', ->
        expect(n(sortByMetadataField: 'foo').sortByMetadataField).to.eq('foo')

      it 'should error when given neither Object IDs nor Node IDs', ->
        expect(-> n(objects: { title: '%s in foo' })).to.throw('Missing option objects.ids or objects.nodeIds')

      it 'should error when given no object title', ->
        expect(-> n(objects: { ids: [ 1, 2 ] })).to.throw('Missing option objects.title')

    describe 'buildQueryJson()', ->
      j = DocumentListParams.buildQueryJson

      it 'should give q', -> expect(j(q: 'foo')).to.deep.eq(q: 'foo')

      it 'should give objects', ->
        expect(j(objects: { ids: [ 1, 2 ], title: '%s in foo' })).to.deep.eq(objects: [ 1, 2 ])

      it 'should give documentIdsBitSetBase64', ->
        expect(j(objects: { documentIdsBitSetBase64: 'QA' }, title: '%s in foo'))
          .to.deep.eq(documentIdsBitSetBase64: 'QA')

      it 'should give nodes', -> # DEPRECATED
        expect(j(objects: { nodeIds: [ 1, 2 ], title: '%s in foo' })).to.deep.eq(nodes: [ 1, 2 ])

      it 'should give tags', ->
        expect(j(tags: { ids: [ 1, 2 ] })).to.deep.eq(tags: [ 1, 2 ])

      it 'should give tagged=true', ->
        expect(j(tags: { tagged: true })).to.deep.eq(tagged: true)

      it 'should give tagged=false', ->
        expect(j(tags: { tagged: false })).to.deep.eq(tagged: false)

      it 'should give tagOperation=all', ->
        expect(j(tags: { ids: [ 1, 2 ], operation: 'all' }))
          .to.deep.eq(tags: [ 1, 2 ], tagOperation: 'all')

      it 'should give tagOperation=none', ->
        expect(j(tags: { ids: [ 1, 2 ], operation: 'none' }))
          .to.deep.eq(tags: [ 1, 2 ], tagOperation: 'none')

      it 'should give sortByMetadataField', ->
        expect(j(sortByMetadataField: 'foo')).to.deep.eq(sortByMetadataField: 'foo')

    describe 'buildQueryString()', ->
      q = DocumentListParams.buildQueryString

      it 'should encodeURIComponent() the q parameter', ->
        expect(q(q: 'foo=bar')).to.eq('q=foo%3Dbar')

      it 'should comma-separate object IDs', ->
        expect(q(objects: { ids: [ 1, 2 ] })).to.eq('objects=1,2')

      it 'should comma-separate node IDs', ->
        expect(q(objects: { nodeIds: [ 1, 2 ] })).to.eq('nodes=1,2')

      it 'should comma-separate tag IDs', ->
        expect(q(tags: { ids: [ 1, 2 ] })).to.eq('tags=1,2')

      it 'should encode tagOperation', ->
        expect(q(tags: { ids: [ 1, 2 ], operation: 'all' })).to.match(/(^|&)tagOperation=all($|&)/)

      it 'should encode tagged=true', ->
        expect(q(tags: { tagged: true })).to.eq('tagged=true')

      it 'should encode tagged=false', ->
        expect(q(tags: { tagged: false })).to.eq('tagged=false')

      it 'should encode sortByMetadataField', ->
        expect(q(sortByMetadataField: 'foo')).to.eq('sortByMetadataField=foo')
