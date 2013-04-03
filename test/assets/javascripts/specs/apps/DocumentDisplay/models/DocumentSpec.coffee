require [
  'apps/DocumentDisplay/models/Document'
], (Document) ->
  describe 'apps/DocumentDisplay/models/Document', ->
    it 'should set urlProperties', ->
      document = new Document({
        text: 'text'
        url: 'https://example.org'
      })

      props = document.get('urlProperties')
      expect(props).toBeDefined()
      expect(props.url).toEqual('https://example.org')

    it 'should set heading to title if available', ->
      document = new Document({
        title: 'title'
        description: 'description'
      })

      heading = document.get('heading')
      expect(heading).toEqual('title')

    it 'should set heading to description if there is no title', ->
      document = new Document({
        description: 'description'
      })

      heading = document.get('heading')
      expect(heading).toEqual('description')

    it 'should set heading to description if there is an empty title', ->
      document = new Document({
        title: ''
        description: 'description'
      })

      heading = document.get('heading')
      expect(heading).toEqual('description')

    it 'should set heading to "" if title and description are empty', ->
      document = new Document({})
      heading = document.get('heading')
      expect(heading).toBe('')
