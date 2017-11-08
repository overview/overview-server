define [
  'underscore'
  'backbone'
  'apps/Show/models/DocumentListParams'
], (_, Backbone, DocumentListParams) ->
  describe 'apps/Show/models/DocumentListParams', ->
    c = (options) -> new DocumentListParams(options)

    describe 'the constructor', ->
      it 'should parse q', -> expect(c(q: 'foo')).to.have.property('q', 'foo')
      it 'should not parse empty q', -> expect(c(q: ' ')).to.have.property('q', null)
      it 'should parse tag IDs', -> expect(c(tags: [ 1, 2 ]).tags).to.deep.eq(ids: [ 1, 2 ])
      it 'should parse tag IDs long-form', -> expect(c(tags: { ids: [ 1, 2 ] }).tags).to.deep.eq(ids: [ 1, 2 ])
      it 'should parse untagged', -> expect(c(tags: { tagged: false }).tags).to.deep.eq(tagged: false)
      it 'should set q=null by default', -> expect(c().q).to.be.null
      it 'should set tags=null by default', -> expect(c().tags).to.be.null
      it 'should set objects=null by default', -> expect(c().objects).to.be.null
      it 'should set sortByMetadataField=null by default', -> expect(c().sortByMetadataField).to.be.null

      it 'should parse tag NONE op', ->
        expect(c(tags: { ids: [1], tagged: false, operation: 'none' }).tags)
          .to.deep.eq(ids: [1], tagged: false, operation: 'none')

      it 'should parse tag ALL op', ->
        expect(c(tags: { ids: [ 1, 2 ], operation: 'all' }).tags)
          .to.deep.eq(ids: [ 1, 2 ], operation: 'all')

      it 'should parse tag ANY op', ->
        expect(c(tags: { ids: [ 1, 2 ], operation: 'any' }).tags).to.deep.eq(ids: [ 1, 2 ])

      it 'should error when given a bad tag op', ->
        expect(-> c(tags: { ids: [ 1, 2 ], operation: 'foo' }))
          .to.throw('Invalid option tags.operation="foo"')

      it 'should error when given a bad tagged value', ->
        expect(-> c(tags: { tagged: 3 })).to.throw('Invalid option tags.tagged=3')

      it 'should error when given a bad tag key', ->
        expect(-> c(tags: { foo: 'bar' })).to.throw('Invalid option tags.foo')

      it 'should parse an object spec with ids', ->
        expect(c(objects: { ids: [ 1, 2 ], title: '%s in foo' }).objects)
          .to.deep.eq(ids: [ 1, 2 ], title: '%s in foo')

      it 'should parse an object spec with nodeIds', ->
        expect(c(objects: { nodeIds: [ 1, 2 ], title: '%s in foo' }).objects)
          .to.deep.eq(nodeIds: [ 1, 2 ], title: '%s in foo')

      it 'should parse an object spec with sortByMetadataField', ->
        expect(c(sortByMetadataField: 'foo').sortByMetadataField).to.eq('foo')

      it 'should error when given neither Object IDs nor Node IDs', ->
        expect(-> c(objects: { title: '%s in foo' })).to.throw('Missing option objects.ids or objects.nodeIds')

      it 'should error when given no object title', ->
        expect(-> c(objects: { ids: [ 1, 2 ] })).to.throw('Missing option objects.title')

    describe 'withTags()', ->
      it 'should clear tags', -> expect(c(tags: { ids: [ 1, 2 ]}).withTags().tags).to.be.null
      it 'should replace tags', -> expect(c(tags: { ids: [ 1 ] }).withTags(ids: [ 2 ]).tags).to.deep.eq(ids: [ 2 ])
      it 'should not modify the original object', ->
        tags1 = { ids: [ 1 ] }
        tags2 = { ids: [ 2 ] }
        params1 = c(tags: tags1)
        params2 = params1.withTags(tags2)
        expect(tags1).to.deep.eq({ ids: [ 1 ] })
        expect(tags2).to.deep.eq({ ids: [ 2 ] })
        expect(params1.tags).to.deep.eq({ ids: [ 1 ] })

    describe 'withObjects()', ->
      it 'should clear objects', ->
        expect(c(objects: { ids: [ 1 ], title: '%s in foo' }).withObjects().objects).to.be.null

      it 'should replace objects', ->
        expect(c(objects: { ids: [ 1 ], title: '%s in foo' }).withObjects(ids: [ 2 ], title: '%s in bar').objects)
          .to.deep.eq(ids: [ 2 ], title: '%s in bar')

      it 'should not modify the original object', ->
        objects1 = { ids: [ 1 ], title: '%s in foo' }
        objects2 = { ids: [ 2 ], title: '%s in bar' }
        params1 = c(objects: objects1)
        params2 = params1.withObjects(params2)
        expect(objects1).to.deep.eq(ids: [ 1 ], title: '%s in foo')
        expect(objects2).to.deep.eq(ids: [ 2 ], title: '%s in bar')
        expect(params1.objects).to.deep.eq(ids: [ 1 ], title: '%s in foo')

    describe 'sortedByMetadataField', ->
      it 'should clear sortByMetadataField', ->
        obj = c(sortByMetadataField: 'foo', title: '%s in foo')
          .sortedByMetadataField()
        expect(obj.sortByMetadataField).to.be.null

      it 'should replace sortByMetadataField', ->
        obj = c(sortByMetadataField: 'foo').sortedByMetadataField('bar')
        expect(obj.sortByMetadataField).to.eq('bar')

      it 'should not modify the original object', ->
        obj = c(sortByMetadataField: 'foo')
        obj.sortedByMetadataField('bar')
        expect(obj.sortByMetadataField).to.eq('foo')

    describe 'reset()', ->
      it 'should clear objects', -> expect(c(objects: { ids: [ 1 ], title: '%s in foo' }).reset().objects).to.be.null
      it 'should clear tags', -> expect(c(tags: { ids: [ 1 ] }).reset().tags).to.be.null
      it 'should clear q', -> expect(c(q: 'foo').reset().q).to.be.null
      it 'should clear sortByMetadataField', -> expect(c(sortByMetadataField: 'foo').reset().sortByMetadataField).to.be.null

    describe 'toJSON()', ->
      it 'should give q', -> expect(c(q: 'foo').toJSON()).to.deep.eq(q: 'foo')

      it 'should give objects', ->
        expect(c(objects: { ids: [ 1, 2 ], title: '%s in foo' }).toJSON()).to.deep.eq(objects: [ 1, 2 ])

      it 'should give nodes', -> # DEPRECATED
        expect(c(objects: { nodeIds: [ 1, 2 ], title: '%s in foo' }).toJSON()).to.deep.eq(nodes: [ 1, 2 ])

      it 'should give tags', ->
        expect(c(tags: { ids: [ 1, 2 ] }).toJSON()).to.deep.eq(tags: [ 1, 2 ])

      it 'should give tagged=true', ->
        expect(c(tags: { tagged: true }).toJSON()).to.deep.eq(tagged: true)

      it 'should give tagged=false', ->
        expect(c(tags: { tagged: false }).toJSON()).to.deep.eq(tagged: false)

      it 'should give tagOperation=all', ->
        expect(c(tags: { ids: [ 1, 2 ], operation: 'all' }).toJSON())
          .to.deep.eq(tags: [ 1, 2 ], tagOperation: 'all')

      it 'should give tagOperation=none', ->
        expect(c(tags: { ids: [ 1, 2 ], operation: 'none' }).toJSON())
          .to.deep.eq(tags: [ 1, 2 ], tagOperation: 'none')

      it 'should not give tagOperation=any, because it is the default', ->
        expect(c(tags: { ids: [ 1, 2 ], operation: 'any' }).toJSON()).to.deep.eq(tags: [ 1, 2 ])

      it 'should give sortByMetadataField', ->
        expect(c(sortByMetadataField: 'foo').toJSON()).to.deep.eq(sortByMetadataField: 'foo')

    describe 'toQueryString()', ->
      it 'should encodeURIComponent() the q parameter', ->
        expect(c(q: 'foo=bar').toQueryString()).to.eq('q=foo%3Dbar')

      it 'should comma-separate object IDs', ->
        expect(c(objects: { ids: [ 1, 2 ], title: '%s in foo' }).toQueryString()).to.eq('objects=1,2')

      it 'should comma-separate node IDs', ->
        expect(c(objects: { nodeIds: [ 1, 2 ], title: '%s in foo' }).toQueryString()).to.eq('nodes=1,2')

      it 'should comma-separate tag IDs', ->
        expect(c(tags: { ids: [ 1, 2 ] }).toQueryString()).to.eq('tags=1,2')

      it 'should encode tagOperation', ->
        expect(c(tags: { ids: [ 1, 2 ], operation: 'all' }).toQueryString()).to.match(/(^|&)tagOperation=all($|&)/)

      it 'should encode tagged=true', ->
        expect(c(tags: { tagged: true }).toQueryString()).to.eq('tagged=true')

      it 'should encode tagged=false', ->
        expect(c(tags: { tagged: false }).toQueryString()).to.eq('tagged=false')

      it 'should encode sortByMetadataField', ->
        expect(c(sortByMetadataField: 'foo').toQueryString()).to.eq('sortByMetadataField=foo')

    describe 'equals()', ->
      # Let's not test too completely; it's just a toJSON() match.
      it 'should return true when true', ->
        p1 = c(tags: { ids: [ 1, 2 ], tagged: false }, objects: { ids: [ 1, 2 ], title: '%s in foo' })
        p2 = c(tags: { ids: [ 1, 2 ], tagged: false }, objects: { ids: [ 1, 2 ], title: '%s in foo' })
        expect(p1.equals(p2)).to.be.true

      it 'should return false when false', ->
        p1 = c(tags: { ids: [ 1, 2 ], tagged: false }, objects: { ids: [ 1, 2 ], title: '%s in foo' })
        p2 = c(tags: { ids: [ 1, 2 ], tagged: true }, objects: { ids: [ 1, 2 ], title: '%s in foo' })
        expect(p1.equals(p2)).to.be.false
