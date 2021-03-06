# trellis-rosid-common

[![Build Status](https://travis-ci.org/trellis-ldp/trellis-rosid-common.png?branch=master)](https://travis-ci.org/trellis-ldp/trellis-rosid-common)
[![Build status](https://ci.appveyor.com/api/projects/status/vij09cj4odyle518?svg=true)](https://ci.appveyor.com/project/acoburn/trellis-rosid-common)
[![Coverage Status](https://coveralls.io/repos/github/trellis-ldp/trellis-rosid-common/badge.svg?branch=master)](https://coveralls.io/github/trellis-ldp/trellis-rosid-common?branch=master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.trellisldp/trellis-rosid-common/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.trellisldp/trellis-rosid-common/)
[![Code Climate](https://codeclimate.com/github/trellis-ldp/trellis-rosid-common/badges/gpa.svg)](https://codeclimate.com/github/trellis-ldp/trellis-rosid-common)


Common classes for Rosid-based implementations of the Trellis API. Rosid is based on a Kafka event bus and a
(potentially distributed) common data store.

The basic principle behind this implementation is to represent resource state as a stream of (re-playable) operations.

## Building

This code requires Java 8 and can be built with Gradle:

    ./gradlew install
