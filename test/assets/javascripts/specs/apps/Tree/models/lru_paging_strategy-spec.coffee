require [
  'apps/Tree/models/lru_paging_strategy'
], (LruPagingStrategy) ->
  describe 'models/lru_paging_strategy', ->
    describe 'LruPagingStrategy', ->
      strategy = undefined

      beforeEach ->
        strategy = new LruPagingStrategy(2)

      it 'should set @n_pages', ->
        expect(strategy.n_pages).toEqual(2)

      it 'should enter starting at 0', ->
        page = strategy.add(0)
        expect(page).toEqual(0)

      it 'should increment', ->
        strategy.add(0)
        page = strategy.add(1)
        expect(page).toEqual(1)

      it 'should throw AllPagesFull when they are', ->
        strategy.add(0)
        strategy.add(1)
        expect(-> strategy.add(2)).toThrow('AllPagesFull')

      it 'should specify the page while freeing', ->
        strategy.add(5)
        strategy.add(3)
        page = strategy.free(3)
        expect(page).toEqual(1)

      it 'should throw IdNotCached when appropriate', ->
        expect(-> strategy.free(3)).toThrow('IdNotCached')

      it 'should throw IdNotCached on freed pages', ->
        strategy.add(3)
        strategy.free(3)
        expect(-> strategy.free(3)).toThrow('IdNotCached')

      it 'should add into freed pages', ->
        strategy.add(2)
        strategy.free(2)
        page = strategy.add(6)
        expect(page).toEqual(0)

      it 'should start with is_full() false', ->
        expect(strategy.is_full()).toBeFalsy()

      it 'should make is_full() true', ->
        strategy.add(2)
        strategy.add(5)
        expect(strategy.is_full()).toBeTruthy()

      it 'should make is_full() false again', ->
        strategy.add(2)
        strategy.add(5)
        strategy.free(2)
        expect(strategy.is_full()).toBeFalsy()

      it 'should find the cache entry to free when there is only one', ->
        strategy.add(3)
        id = strategy.find_id_to_free()
        expect(id).toEqual(3)

      it 'should make find_id_to_free() return undefined when there are no IDs', ->
        expect(strategy.find_id_to_free()).toBeUndefined()

      it 'should find the least-recently-used id to remove', ->
        strategy.add(3)
        strategy.add(4)
        strategy.touch(3)
        expect(strategy.find_id_to_free()).toEqual(4)

      it 'should find_id_to_free_if_full() when not full', ->
        strategy.add(3)
        expect(strategy.find_id_to_free_if_full()).toBeUndefined()

      it 'should find_id_to_free_if_full() when full', ->
        strategy.add(5)
        strategy.add(3)
        expect(strategy.find_id_to_free_if_full()).toEqual(5)

      it 'should find_id_to_free() on a non-frozen ID', ->
        strategy.add(4)
        strategy.add(7)
        strategy.freeze(4)
        expect(strategy.find_id_to_free()).toEqual(7)

      it 'should throw "AllPagesFrozen" if nothing can be freed', ->
        strategy.add(7)
        strategy.freeze(7)
        strategy.add(1)
        strategy.freeze(1)
        expect(-> strategy.find_id_to_free()).toThrow('AllPagesFrozen')

      it 'should throw "AllPagesFrozen" from find_id_to_free_if_full() too', ->
        strategy.add(7)
        strategy.freeze(7)
        strategy.add(1)
        strategy.freeze(1)
        expect(-> strategy.find_id_to_free_if_full()).toThrow('AllPagesFrozen')

      it 'should thaw pages', ->
        strategy.add(7)
        strategy.add(1)
        strategy.thaw(7)
        expect(strategy.find_id_to_free()).toEqual(7)

      it 'should implement is_frozen()', ->
        strategy.add(7)
        expect(strategy.is_frozen(7)).toBe(false)
        strategy.freeze(7)
        expect(strategy.is_frozen(7)).toBe(true)
        strategy.thaw(7)
        expect(strategy.is_frozen(7)).toBe(false)
