# This is in its own file for initialization purposes. It sets a global variable.
Log = require('models/log').Log

require('globals').logger = new Log()
