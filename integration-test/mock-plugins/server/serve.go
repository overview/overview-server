package main

import (
  "io/ioutil"
  "log"
  "net/http"
)

func logRequest(r *http.Request) {
  log.Printf("%s %s %s\n", r.RemoteAddr, r.Method, r.URL.String())
}

func metadataHandler(w http.ResponseWriter, r *http.Request) {
  logRequest(r)
  w.Header().Set("Access-Control-Allow-Origin", "*")
  w.Header().Set("Access-Control-Allow-Methods", "GET")
  w.Header().Set("Cache-Control", "private; no-cache")
  w.Header().Set("Content-Type", "application/json")
  w.WriteHeader(http.StatusOK)
  w.Write([]byte{'{', '}'})
}

func showHandler(w http.ResponseWriter, r *http.Request) {
  bytes, err := ioutil.ReadFile("/show.html")
  if err != nil {
    log.Fatal(err)
  }

  logRequest(r)
  w.Header().Set("Content-Type", "text/html; charset=utf-8")
  w.Header().Set("Cache-Control", "private; no-cache")
  w.WriteHeader(http.StatusOK)
  w.Write(bytes)
}

func main() {
  http.HandleFunc("/metadata", metadataHandler)
  http.HandleFunc("/show", showHandler)
  log.Println("Simple Overview-plugin web server listening to http://0.0.0.0:80")
  log.Fatal(http.ListenAndServe(":80", nil))
}
