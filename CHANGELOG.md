# Changelog
All notable changes to this project will be documented in this file.

## [Unreleased]

### Changed

 - Specify the set of well known HTTP Content-Type values as AWS expects them.
   This fixes issues where request signature calculated by the server do not match
   the signature provided by the client.

 - Improve correctness and resilience in tests. 

## [1.2.0]
### New

 - Add optional Akka HttpExt parameter to client constructor, allowing
   the usage of a specific instance over the one associated with the current actor system.

### Changed

 - Add slf4j simple logger configuration for tests 

## [1.1.0]
### Changed

 - Populate `Content-Type` and `Content-Length` headers correctly
   when transforming the Akka HttpResponse to the AWS SDK Response

## [1.0.1]
### Changed

- Improved build metadata

## [1.0.0]
### New

- Initial release
  
