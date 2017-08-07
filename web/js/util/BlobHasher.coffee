define [
  'underscore'
  'jssha/src/sha1'
], (_, sha1) ->
  DEFAULTS =
    blockSize: 10 * 1024 * 1024
    fileReader: FileReader

  CODE_0 = '0'.charCodeAt(0)
  CODE_9 = '9'.charCodeAt(0)
  CODE_a = 'a'.charCodeAt(0)
  CODE_f = 'f'.charCodeAt(0)
  h2b = (h) ->
    if CODE_0 <= h <= CODE_9
      h - CODE_0
    else if CODE_a <= h <= CODE_f
      10 + h - CODE_a
    else
      throw new Error("Invalid character code: #{h}")

  hexToArrayBuffer = (hex) ->
    uint8Array = new Uint8Array(hex.length / 2)
    for i in [ 0 ... hex.length / 2 ]
      b1 = h2b(hex.charCodeAt(i * 2))
      b2 = h2b(hex.charCodeAt(i * 2 + 1))
      b = (b1 << 4) | b2
      uint8Array[i] = b
    uint8Array.buffer

  # Calculates hashes for Blobs (files) asynchronously.
  #
  # One key advantage of this utility is that it will iterate over large files
  # instead of reading entire files into memory.
  class BlobHasher
    # Reads up to `options.blockSize` (default 10MB) bytes from `blob` using
    # `options.fileReader` (default FileReader).
    #
    # Calls `options.progress(arrayBuffer)` for each block. Calls
    # `done(errorOrNull)` when complete.
    _step: (blob, options, done) ->
      return done(null) if blob.size == 0 # base case

      recurse = =>
        nextSlice = blob.slice(options.blockSize)
        @_step(nextSlice, options, done)

      reader = new (options.fileReader)()
      reader.onload = ->
        options.progress(reader.result)
        recurse()
      reader.onerror = (evt) -> done(evt)

      thisSlice = blob.slice(0, options.blockSize)
      reader.readAsArrayBuffer(thisSlice)

    # Calculates a sha1 hash
    sha1: (blob, options, callback) ->
      if !callback?
        callback = options
        options = {}

      hasher = new sha1('SHA-1', 'ARRAYBUFFER')

      options = _.extend({
        progress: (arrayBuffer) -> hasher.update(arrayBuffer)
      }, DEFAULTS, options)

      @_step blob, options, (err) ->
        return callback(err) if err?
        # Horrid hash API
        hashHex = hasher.getHash('HEX')
        hashBuffer = hexToArrayBuffer(hashHex)
        parsedHash = (x.toString(16) for x in new Uint8Array(hashBuffer)).join('')
        callback(null, hashBuffer)
