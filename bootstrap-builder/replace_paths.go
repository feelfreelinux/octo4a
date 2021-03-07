package main

import (
	"bytes"
	"fmt"
	"log"
	"os"
	"path/filepath"
)

var REPLACE_FROM = []byte("com.termux")
var REPLACE_TO = []byte("com.octo4a")

func main() {
	if len(os.Args) < 2 {
		log.Fatalf("usage: replace_path <dir_path>")
	}
	err := filepath.Walk(os.Args[1],
		func(path string, info os.FileInfo, err error) error {
			if err != nil {
				return err
			}
			if info.IsDir() {
				return nil
			}
			data, err := os.ReadFile(path)
			if err != nil {
				return fmt.Errorf("failed to read file %v: %w", path, err)
			}
			out := bytes.Replace(data, REPLACE_FROM, REPLACE_TO, -1)
			err = os.WriteFile(path, out, info.Mode())
			if err != nil {
				return fmt.Errorf("failed to read write %v: %w", path, err)
			}
			return nil
		})
	if err != nil {
		log.Fatal(err)
	}
}
