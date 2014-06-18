define [ 'apps/Tree/models/Tag' ], (Tag) ->
  describe 'apps/Tree/models/Tag', ->
    it 'should assign a color by default', ->
      tag = new Tag(name: 'foo')
      expect(tag.get('color')).to.match(/#[0-9a-z]{6}/)

    it 'should not assign the same color to different tags', ->
      tag1 = new Tag(name: 'foo')
      tag2 = new Tag(name: 'bar')
      expect(tag1.get('color')).not.to.eq(tag2.get('color'))
