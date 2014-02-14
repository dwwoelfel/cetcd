#!/usr/bin/env bash

set -x
set -e

wget https://github.com/coreos/etcd/releases/download/v0.3.0/etcd-v0.3.0-linux-amd64.tar.gz

tar xvfz etcd-*.tar.gz
cd etcd-*
nohup bash -c "./etcd &"
