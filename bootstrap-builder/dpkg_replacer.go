package main

import (
  "archive/tar"
  "bytes"
  "errors"
  "flag"
  "fmt"
  "io"
  "log"
  "os"
  "strings"

  "github.com/blakesmith/ar"
  "github.com/ulikunitz/xz"
)

var REPLACE_FROM = []byte("com.termux")
var REPLACE_TO = []byte("com.octo4a")

func ReplaceInReader(r io.Reader, w io.Writer) error {
  data, err := io.ReadAll(r)
  if err != nil {
    return fmt.Errorf("failed to read from reader: %w", err)
  }
  out := bytes.Replace(data, REPLACE_FROM, REPLACE_TO, -1)
  r2 := bytes.NewReader(out)
  n, err := io.Copy(w, r2)
  if err != nil {
    return fmt.Errorf("failed to write to writer: %w", err)
  }
  log.Printf("n == %v, len(out) == %v", n, len(out))
  return nil
}

func ReplaceTarFile(inFile io.Reader, outFile io.Writer) error {
  tarFile := tar.NewReader(inFile)

  outTar := tar.NewWriter(outFile)
  for {
    header, err := tarFile.Next()
    if errors.Is(err, io.EOF) {
      break
    }
    if err != nil {
      return fmt.Errorf("failed to read from tar file: %w", err)
    }
    header.Name = strings.ReplaceAll(header.Name, string(REPLACE_FROM), string(REPLACE_TO))
    if err := outTar.WriteHeader(header); err != nil {
      return fmt.Errorf("failed to write header to tar file: %w", err)
    }
    log.Printf("name == %v, headerSize = %v", header.Name, header.Size)
    if header.Size != 0 {
      if err := ReplaceInReader(tarFile, outTar); err != nil {
        return fmt.Errorf("failed to replace in reader in tar: %w", err)
      }
    }

  }

  outTar.Close()
  return nil
}

func ReplaceInArFile(inFile io.Reader, outFile io.Writer) error {
  arFile := ar.NewReader(inFile)

  outAr := ar.NewWriter(outFile)
  outAr.WriteGlobalHeader()
  for {
    header, err := arFile.Next()
    if errors.Is(err, io.EOF) {
      break
    }
    if err != nil {
      return fmt.Errorf("failed to read heade from ar file: %w", err)
    }

    buf := &bytes.Buffer{}
    if header.Name == "data.tar.xz/" {
      xzReader, err := xz.NewReader(arFile)
      if err != nil {
        return fmt.Errorf("failed to create xzReader: %w", err)
      }
      header.Name = "data.tar/"
      err = ReplaceTarFile(xzReader, buf)
      if err != nil {
        return fmt.Errorf("failed to replace xz compressed tar file: %w", err)
      }

    } else {
      log.Printf("Unknown file type %v, leaving as is", header.Name)
      _, err := io.Copy(buf, arFile)
      if err != nil {
        return fmt.Errorf("failed to copy file %v inside of ar archive: %w", header.Name, err)
      }
    }
    header.Size = int64(buf.Len())
    err = outAr.WriteHeader(header)
    if err != nil {
      return fmt.Errorf("failed to write ar header: %w", err)
    }
    _, err = outAr.Write(buf.Bytes())
    if err != nil {
      return fmt.Errorf("failed to write ar data: %w", err)
    }
  }
  return nil
}

func main() {
  fmt.Println("BRUH\n")
  flag.Parse()
  if len(flag.Args()) < 1 {
    log.Fatal("missing file")
  }
  fmt.Println("BRUH\n")
  f, err := os.Open(flag.Args()[0])
  if err != nil {
    log.Fatal("failed to open input .deb file: %v", err)
  }
  defer f.Close()
  outFile, err := os.Create(flag.Args()[0] + ".replaced")
  if err != nil {
    log.Fatal("failed to open output .deb file: %v", err)
  }
  defer outFile.Close()
  if err := ReplaceInArFile(f, outFile); err != nil {
    log.Fatalf("failed to replace in ar file: %v", err)
  }
}
