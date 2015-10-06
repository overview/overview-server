define [
  'util/BlobHasher'
], (BlobHasher) ->
  hex = (buffer) ->
    uint8Array = new Uint8Array(buffer)
    "0x" + (i.toString(16) for i in uint8Array).join('')

  describe 'util/BlobHasher', ->
    beforeEach ->
      @blob = new Blob([ 'foobarbaz' ])
      @hasher = new BlobHasher()
      @sha1 = "0x5f5513f8822fdbe5145af33b64d8d970dcf95c6e"

    it 'should calculate the sha1', (done) ->
      @hasher.sha1 @blob, (err, result) =>
        expect(hex(result)).to.eq(@sha1)
        done(err)

    it 'should calculate the sha1 in blocks', (done) ->
      @hasher.sha1 @blob, { blockSize: 3 }, (err, result) =>
        expect(hex(result)).to.eq(@sha1)
        done(err)

    it 'should calculate the sha1 in blocks when the final block is incomplete', (done) ->
      @hasher.sha1 @blob, { blockSize: 6 }, (err, result) =>
        expect(hex(result)).to.eq(@sha1)
        done(err)

    it 'should throw an error when FileReader gives an error', (done) ->
      fileReader = ->
      fileReader.prototype.readAsArrayBuffer = -> @onerror(error: "NotFoundError")
      fileReader.prototype.onerror = ->

      @hasher.sha1 @blob, { fileReader: fileReader }, (err, result) =>
        expect(err).to.exist
        done()
