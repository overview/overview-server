define [
  'underscore'
  'backbone'
  'apps/Show/models/DocumentListParams'
], (_, Backbone, DocumentListParams) ->
  describe 'apps/Show/models/DocumentListParams', ->
    describe 'normalize', ->
      n = DocumentListParams.normalize

      it 'should parse q', -> expect(n(q: 'foo')).to.deep.eq(q: 'foo')
      it 'should not parse empty q', -> expect(n(q: ' ')).to.deep.eq({})
      it 'should parse tag IDs', -> expect(n(tags: [ 1, 2 ])).to.deep.eq(tags: [ 1, 2 ])
      it 'should parse tag IDs long-form', -> expect(n(tags: [ 1, 2 ])).to.deep.eq(tags: [ 1, 2 ])
      it 'should parse untagged', -> expect(n(tagged: false)).to.deep.eq(tagged: false)

      it 'should parse tag NONE op', ->
        expect(n(tags: [1], tagOperation: 'none')).to.deep.eq(tags: [1], tagOperation: 'none')

      it 'should parse tag ALL op', ->
        expect(n(tags: [1], tagOperation: 'all')).to.deep.eq(tags: [1], tagOperation: 'all')

      it 'should parse tag ANY op and omit it, because any is the default', ->
        expect(n(tags: [1], tagOperation: 'any')).to.deep.eq(tags: [1])

      it 'should error when given a bad tag op', ->
        expect(-> n(tags: [1], tagOperation: 'foo'))
          .to.throw('Invalid option tagOperation="foo"')

      it 'should error when given a bad tagged value', ->
        expect(-> n(tagged: 3)).to.throw('Invalid option options.tagged=3')

      it 'should parse an object spec with ids', ->
        expect(n(objects: [ 1, 2 ], title: '%s in foo'))
          .to.deep.eq(objects: [ 1, 2 ], title: '%s in foo')

      it 'should parse an object spec with nodes', ->
        expect(n(nodes: [ 1, 2 ], title: '%s in foo'))
          .to.deep.eq(nodes: [ 1, 2 ], title: '%s in foo')

      it 'should parse an object spec with sortByMetadataField', ->
        expect(n(sortByMetadataField: 'foo')).to.deep.eq(sortByMetadataField: 'foo')

      it 'should error when given neither Object IDs nor Node IDs', ->
        expect(-> n(title: '%s in foo')).to.throw('Missing options.ids or options.nodes or options.documentIdsBitSetBase64')

      it 'should error when given no object title', ->
        expect(-> n(objects: [ 1, 2 ])).to.throw('Missing options.title')

    describe 'buildQueryJson()', ->
      j = DocumentListParams.buildQueryJson

      it 'should give q', -> expect(j(q: 'foo')).to.deep.eq(q: 'foo')

      it 'should give objects', ->
        expect(j(objects: [ 1, 2 ], title: '%s in foo')).to.deep.eq(objects: [ 1, 2 ])

      it 'should give documentIdsBitSetBase64', ->
        expect(j(documentIdsBitSetBase64: 'QA')).to.deep.eq(documentIdsBitSetBase64: 'QA')

      it 'should give nodes', -> # DEPRECATED
        expect(j(nodes: [ 1, 2 ], title: '%s in foo')).to.deep.eq(nodes: [ 1, 2 ])

      it 'should give tags', ->
        expect(j(tags: [ 1, 2 ])).to.deep.eq(tags: [ 1, 2 ])

      it 'should give tagged=true', ->
        expect(j(tagged: true)).to.deep.eq(tagged: true)

      it 'should give tagged=false', ->
        expect(j(tagged: false)).to.deep.eq(tagged: false)

      it 'should give tagOperation', ->
        expect(j(tags: [ 1, 2 ], tagOperation: 'all'))
          .to.deep.eq(tags: [ 1, 2 ], tagOperation: 'all')

      it 'should give sortByMetadataField', ->
        expect(j(sortByMetadataField: 'foo')).to.deep.eq(sortByMetadataField: 'foo')

    describe 'extend', ->
      e = DocumentListParams.extend

      it 'should add different parts', ->
        expect(e({ q: 'foo' }, { tagged: true })).to.deep.eq({ tagged: true, q: 'foo' })

      it 'should replace the same part', ->
        expect(e({ q: 'foo', tagged: true }, { tags: [ 1 ] })).to.deep.eq({ q: 'foo', tags: [ 1 ] })

      it 'should empty q when given null', ->
        expect(e({ q: 'foo' }, { q: null })).to.deep.eq({})

      it 'should empty tags when given null', ->
        expect(e({ tagged: true }, { tags: null })).to.deep.eq({})

      it 'should empty tags when given empty Array', ->
        expect(e({ tagged: true }, { tags: [] })).to.deep.eq({})

      it 'should empty objects when given null objects', ->
        expect(e({ title: 'foo', objects: [ 1 ] }, { objects: null })).to.deep.eq({})

    describe 'buildQueryString()', ->
      q = DocumentListParams.buildQueryString

      it 'should encodeURIComponent() the q parameter', ->
        expect(q(q: 'foo=bar')).to.eq('q=foo%3Dbar')

      it 'should comma-separate object IDs', ->
        expect(q(objects: [ 1, 2 ])).to.eq('objects=1,2')

      it 'should comma-separate node IDs', ->
        expect(q(nodes: [ 1, 2 ])).to.eq('nodes=1,2')

      it 'should comma-separate tag IDs', ->
        expect(q(tags: [ 1, 2 ])).to.eq('tags=1,2')

      it 'should encode tagOperation', ->
        expect(q(tags: [ 1, 2 ], tagOperation: 'all')).to.match(/(^|&)tagOperation=all($|&)/)

      it 'should encode tagged=true', ->
        expect(q(tagged: true)).to.eq('tagged=true')

      it 'should encode tagged=false', ->
        expect(q(tagged: false)).to.eq('tagged=false')

      it 'should encode sortByMetadataField', ->
        expect(q(sortByMetadataField: 'foo')).to.eq('sortByMetadataField=foo')
