define [ 'apps/UserAdmin/collections/Users' ], (Users) ->
  describe 'apps/UserAdmin/collections/Users', ->
    subject = undefined

    beforeEach ->
      subject = new Users [],
        pagination:
          page: 1

    it 'should have a pagination', ->
      expect(subject.pagination).to.deep.eq(page: 1)

    it 'should add pagination to URL', ->
      expect(subject.url()).to.match(/.*\?page=1$/)

    it 'should add search to URL', ->
      subject.pagination.search = 'a&b'
      expect(subject.url()).to.match(/.*&search=a%26b$/)

    it 'should add sortBy to URL', ->
      subject.pagination.sortBy = 'email'
      expect(subject.url()).to.match(/.*&sortBy=email$/)

    it 'should parse the ".users" part', ->
      subject.set({
        users: [
          {
            email: 'user@example.org'
            is_admin: true
          },
          {
            email: 'user2@example.org'
            is_admin: false
          }
        ]
      }, parse: true)
      expect(subject.length).to.eq(2)

    afterEach ->
      subject.off()

    it 'should trigger :parse-pagination with page, pageSize, total', ->
      spy = sinon.spy()
      subject.on('parse-pagination', spy)
      subject.set({
        page: 2
        pageSize: 10
        total: 23
        users: []
      }, parse: true)
      expect(spy).to.have.been.calledWith
        page: 2
        pageSize: 10
        total: 23

