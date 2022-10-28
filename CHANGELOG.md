# Changelog
All notable changes to this project will be documented in this file.

## [Unreleased]

### Changed

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
  
