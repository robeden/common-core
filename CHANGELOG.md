# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).


## [1.1.1] - 2017-10-31
### Fixed
- Greatly simplify logic in in `SingleThreadRepeatingTask` to resolve a possible
  race condition.

## [1.1.0] - 2016-11-02
### Added
- Add `Pair` and `Triple` classes.

### Changed
- Move `IOKit` from the `com.logicartisan.common.core.io` package to 
  `com.logicartisan.common.core`. 

## [1.0.0] - 2016-10-31
- Initial release, slimmed down from
  [StarLight Common](https://bitbucket.org/robeden/starlight-common).