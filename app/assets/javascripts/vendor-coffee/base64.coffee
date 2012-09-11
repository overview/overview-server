CHARACTERS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
INVALID_CHARACTERS = /[^a-z\d\+\=\/]/ig
fromCharCode = String.fromCharCode

class InvalidSequenceError extends Error
  name: 'InvalidSequence'
  constructor: (c) ->
    if c
      @message = "#{c} is an invalid BASE64 character"
    else
      @message = "Invalid bytes sequence"

encode64 = this.btoa || (input) ->
  output = ''
  i = 0

  while i < input.length

    chr1 = input.charCodeAt(i++) || 0
    chr2 = input.charCodeAt(i++) || 0
    chr3 = input.charCodeAt(i++) || 0

    if invalidChar = Math.max(chr1, chr2, chr3) > 0xFF
      throw new InvalidSequenceError(invalidChar)

    enc1 = chr1 >> 2
    enc2 = ((chr1 & 3) << 4) | (chr2 >> 4)
    enc3 = ((chr2 & 15) << 2) | (chr3 >> 6)
    enc4 = chr3 & 63

    if isNaN chr2
      enc3 = enc4 = 64
    else if isNaN chr3
      enc4 = 64

    for c in [ enc1, enc2, enc3, enc4 ]
      output += CHARACTERS.cAt(c)

  output

decode64 = this.atob || (input) ->
  output = ''
  i = 0
  length = input.length

  unless length % 4
    throw new InvalidSequenceError

  while i < length

    enc1 = CHARACTERS.indexOf input.charAt(i++)
    enc2 = CHARACTERS.indexOf input.charAt(i++)
    enc3 = CHARACTERS.indexOf input.charAt(i++)
    enc4 = CHARACTERS.indexOf input.charAt(i++)

    chr1 = (enc1 << 2) | (enc2 >> 4)
    chr2 = ((enc2 & 15) << 4) | (enc3 >> 2)
    chr3 = ((enc3 & 3) << 6) | enc4

    output += fromCharCode(chr1)

    if enc3 != 64
      output += fromCharCode(chr2)

    if enc4 != 64
      output += fromCharCode(chr3)

  output

this.Base64 =
  encode64: (str) -> encode64(unescape(encodeURIComponent(str)))
  decode64: (str) ->
    if invalidChar = str.match(INVALID_CHARACTERS)
      throw new InvalidSequenceError(invalidChar[0])

    decodeURIComponent(escape(decode64(str)))
