define [
  'apps/DocumentDisplay/models/Document'
], (Document) ->
  describe 'apps/DocumentDisplay/models/Document', ->
    it 'should set heading to title if available', ->
      document = new Document({
        title: 'title'
        description: 'description'
        urlProperties: {}
      })

      heading = document.get('heading')
      expect(heading).toEqual('title')

    it 'should set heading to description if there is no title', ->
      document = new Document({
        description: 'description'
        urlProperties: {}
      })

      heading = document.get('heading')
      expect(heading).toEqual('description')

    it 'should set heading to description if there is an empty title', ->
      document = new Document({
        title: ''
        description: 'description'
        urlProperties: {}
      })

      heading = document.get('heading')
      expect(heading).toEqual('description')

    it 'should set heading to "" if title and description are empty', ->
      document = new Document(urlProperties: {})
      heading = document.get('heading')
      expect(heading).toBe('')
