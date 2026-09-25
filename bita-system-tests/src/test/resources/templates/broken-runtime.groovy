// A template that parses but fails while building its route — stands in for a bad config
// version (e.g. one whose required parameter is missing) reaching the ESB.
throw new IllegalStateException("intentionally broken template")
