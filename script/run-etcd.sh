#!/usr/bin/env bash

set -x
set -e

wget https://github.com/coreos/etcd/releases/download/v0.2.0/etcd-v0.2.0-Linux-x86_64.tar.gz

tar xvfz etcd-*.tar.gz
cd etcd-*
nohup bash -c "./etcd &"
