define [
  'util/BlobHasher'
], (BlobHasher) ->
  # Replacement Blob constructor, for PhantomJS <2.0
  #
  # Once we upgrade to PhantomJS 2.0, we can drop this and use new Blob()
  # instead.
  #
  # https://github.com/ariya/phantomjs/issues/11013
  buildBlob = (asciiString) ->
    if (typeof(Blob) == 'function')
      new Blob(asciiString)
    else
      builder = new WebKitBlobBuilder()
      builder.append(asciiString)
      ret = builder.getBlob()
      aliasedSlice = (args...) ->
        ret = @webkitSlice(args...)
        ret.slice = aliasedSlice
        ret
      ret.slice = aliasedSlice
      ret

  hex = (buffer) ->
    uint8Array = new Uint8Array(buffer)
    "0x" + (i.toString(16) for i in uint8Array).join('')

  describe.only 'util/BlobHasher', ->
    beforeEach ->
      @blob = buildBlob("foobarbaz")
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
