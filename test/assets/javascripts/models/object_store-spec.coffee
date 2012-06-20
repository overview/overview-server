ObjectStore = require('models/object_store').ObjectStore

count_object_keys = (o) ->
  n = 0
  for _, __ of o
    n += 1
  n

describe 'models/object_store', ->
  describe 'ObjectStore', ->
    store = undefined

    beforeEach ->
      store = new ObjectStore()

    it 'should start empty', ->
      count = count_object_keys(store.objects)
      expect(count).toBe(0)

    it 'should get() a object based on id', ->
      object = { id: 1 }
      store.add(object)
      object2 = store.get(object.id)
      expect(object2).toBe(object)

    it 'should delete an item on remove()', ->
      object = { id: 1 }
      store.add(object)
      store.remove(object)
      object2 = store.get(object.id)
      expect(object2).toBeUndefined()

    it 'should not delete an item that has been added twice', ->
      object = { id: 1 }
      store.add(object)
      store.add(object)
      store.remove(object)
      object2 = store.get(object.id)
      expect(object2).toBe(object)
