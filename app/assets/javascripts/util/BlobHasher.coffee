define [
  'underscore'
  'sha1'
], (_, Sha1) ->
  DEFAULTS =
    blockSize: 10 * 1024 * 1024
    fileReader: FileReader

  # CryptoJS wants a WordArray, which looks like this:
  #
  # wordArray = {
  #   words: [ /* Array of 32-bit big-Endian words */ ],
  #   sigBytes: /* number of bytes */
  # }
  arrayBufferToWordArray = (arrayBuffer) ->
    dataView = new DataView(arrayBuffer)

    nCompleteWords = Math.floor(dataView.byteLength / 4)

    # Calculate all complete words. DataView.getUint32() is big-endian.
    words = (dataView.getUint32(pos * 4) for pos in [ 0 ... nCompleteWords ])

    # Calculate the last word
    if nCompleteWords * 4 < arrayBuffer.byteLength
      lastWord = 0x0
      lastInts = new Uint8Array(arrayBuffer, nCompleteWords * 4)
      for i in [ 0 ... 4 ]
        uint8 = if lastInts.length > i then lastInts[i] else 0
        lastWord = (lastWord << 8) | uint8
      words.push(lastWord)

    words: words
    sigBytes: arrayBuffer.byteLength

  # CryptoJS returns a WordArray, but we return an ArrayBuffer
  wordArrayToArrayBuffer = (wordArray) ->
    uint32Array = new Uint32Array(wordArray.words.length)
    dataView = new DataView(uint32Array.buffer) # big-endian

    wordArray.words.forEach (uint32, index) ->
      dataView.setUint32(index * 4, uint32)

    uint32Array.buffer

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

      hasher = CryptoJS.algo.SHA1.create()

      options = _.extend({
        progress: (arrayBuffer) ->
          wordArray = arrayBufferToWordArray(arrayBuffer)
          hasher.update(wordArray)
      }, DEFAULTS, options)

      @_step blob, options, (err) ->
        return callback(err) if err?
        hashWordArray = hasher.finalize()
        hashBuffer = wordArrayToArrayBuffer(hashWordArray)
        callback(null, hashBuffer)
