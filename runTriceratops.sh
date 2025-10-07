#!/bin/sh

mkdir -p ~/.Triceratops

docker run --rm -it \
  -v ~/.Triceratops:/app/output \
  --user "$(id -u):$(id -g)" \
  -e HOME=/app/output \
  triceratops-app "-docker"
