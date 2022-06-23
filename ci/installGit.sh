#!/bin/bash -x

which git
git --version 2>1 /dev/null

gitVersionCommandReturnValue=$?

if test "$gitVersionCommandReturnValue" != 0
then
  echo "Installing Git..."
  apt-get -y update
  apt-get -y install git
  which git
  git --version
fi
