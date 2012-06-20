DomIdGenerator = require('models/dom_id_generator').DomIdGenerator

describe 'DomIdGenerator', ->
  describe 'next()', ->
    it 'should start with 1', ->
      gen = new DomIdGenerator('foo')
      id = gen.next()
      expect(id).toEqual('foo-1')

    it 'should then give 2', ->
      gen = new DomIdGenerator('foo')
      id = gen.next()
      id2 = gen.next()
      expect(id2).toEqual('foo-2')

  describe 'node_to_guaranteed_dom_id', ->
    it 'should set an ID when necessary', ->
      gen = new DomIdGenerator('foo')
      o = {}
      id = gen.node_to_guaranteed_dom_id(o)
      expect(id).toEqual('foo-1')
      expect(o.id).toEqual('foo-1')

    it 'should return ID when an ID is already set', ->
      gen = new DomIdGenerator('foo')
      o = { id: 'bar' }
      id = gen.node_to_guaranteed_dom_id(o)
      expect(id).toEqual('bar')
      expect(o.id).toEqual('bar')
