# define [ 'apps/MassUploadForm/views/UploadProgress', 'backbone' ], (UploadProgress, Backbone) ->
#   Upload = Backbone.Model.extend
#     defaults:
#       loaded: 0
#       total: 0
#     getProgress: -> { loaded: @get('loaded'), total: @get('total') }

#   buildUpload = (loaded, total) ->
#     new Upload({ loaded: loaded, total: total })

#   describe 'MassUpload/UploadProgress', ->
#     collection = undefined
#     subject = undefined

#     beforeEach ->
#       collection = new Backbone.Collection()
#       subject = new UploadProgress({ collection: collection })

#     it 'should start with 0/0', ->
#       expect(subject.get('loaded')).toEqual(0)
#       expect(subject.get('total')).toEqual(0)

#     it 'should start with not 0/0 if there are uploads already', ->
#       collection = new Backbone.Collection([ buildUpload(10, 20) ])
#       subject = new UploadProgress({ collection: collection })
#       expect(subject.get('loaded')).toEqual(10)
#       expect(subject.get('total')).toEqual(20)

#     it 'should add to total when an upload is added', ->
#       collection.add(buildUpload(10, 20))
#       expect(subject.get('total')).toEqual(20)

#     it 'should add to loaded when an upload is added', ->
#       collection.add(buildUpload(10, 20))
#       expect(subject.get('loaded')).toEqual(10)

#     it 'should ignore a set that happens before add is complete (race condition)', ->
#       collection = new Backbone.Collection()
#       collection.on('add', (model) -> model.set('foo', 'bar'))
#       subject = new UploadProgress({ collection: collection })
#       collection.add(buildUpload(10, 20))
#       expect(subject.pick('loaded', 'total')).toEqual({ loaded: 10, total: 20 })

#     describe 'starting with some uploads', ->
#       beforeEach ->
#         collection.add([
#           buildUpload(10, 20)
#           buildUpload(20, 40)
#           buildUpload(40, 80)
#         ])

#       it 'should add correctly', ->
#         # This just tests the test suite...
#         expect(subject.get('loaded')).toEqual(70)
#         expect(subject.get('total')).toEqual(140)

#       it 'should subtract from loaded when an upload is removed', ->
#         collection.remove(collection.at(1))
#         expect(subject.get('loaded')).toEqual(50)

#       it 'should subtract from total when an upload is removed', ->
#         collection.remove(collection.at(1))
#         expect(subject.get('total')).toEqual(100)

#       it 'should change loaded when an upload changes', ->
#         collection.at(1).set({ loaded: 25 })
#         expect(subject.get('loaded')).toEqual(75)

#       it 'should change total when an upload changes', ->
#         collection.at(1).set({ total: 25 })
#         expect(subject.get('total')).toEqual(125)

#       it 'should change loaded and total on reset', ->
#         collection.reset([ buildUpload(20, 30) ])
#         expect(subject.get('loaded')).toEqual(20)
#         expect(subject.get('total')).toEqual(30)
