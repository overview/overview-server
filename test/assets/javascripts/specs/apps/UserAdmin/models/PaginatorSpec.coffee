define [ 'apps/UserAdmin/models/Paginator' ], (Paginator) ->
  describe 'apps/UserAdmin/models/Paginator', ->
    subject = undefined

    beforeEach ->
      subject = new Paginator()

    it 'should start with page=1', ->
      expect(subject.get('page')).toEqual(1)
