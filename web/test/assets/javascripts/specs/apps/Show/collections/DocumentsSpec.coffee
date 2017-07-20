define [
  'apps/Show/collections/Documents'
], (Documents) ->
  describe 'apps/Show/collections/Documents', ->
    beforeEach ->
      @documents = new Documents()

    afterEach ->
      @documents.off()
