// Shortcuts for various timeouts.
//
// We never allow arbitrary integer timeout values: we only allow keywords. (If
// the test waits, we must explain intent.)
//
// These timeouts are chosen to satisfy two conflicting criteria:
//
// * Timeouts must be upper bounds. If you're waiting 2s for something to
//   happen, you must be able to say to yourself, "if it takes 2,001ms, that's
//   a bug."
// * Timeouts must be as brief as possible. Two benefits: we catch egregious
//   performance problems, and our test suite completes more quickly when tests
//   fail. (Most failures are timeouts: we wait for an element to appear and it
//   never appears.)
module.exports = {
  true: 1000,     // default
  fast: 1000,     // everyday operations (worst case)
  pageLoad: 5000, // not _fast_, but nothing should hold it up
  slow: 60000,    // a progress bar is visible
}
