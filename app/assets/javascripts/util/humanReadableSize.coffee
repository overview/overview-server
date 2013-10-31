define ->
  # Returns human-readable, brief byte size string.
  #
  # For instance:
  #
  # 1024 -> "1 kB"
  # 500 -> "500 B"
  # 700 -> "0.7 kB"
  humanReadableSize = (size) ->
    units = [
      'kB'
      'MB'
      'GB'
      'TB'
    ]
    s = "#{size} B"
    for unit in units
      size /= 1024
      if size <= 512
        s = "#{size.toFixed(1)} #{unit}"
        break
    s
