# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).


## [unreleased]
### Removed
- `IOKit` has been removed.
- `Pair` and `Triple` are no longer serializable and are now immutable.
- `ListenerSupportFactory` has been removed.

### Changed
- `SharedThreadPool.INSTANCE` now implements `ScheduledExecutor`. Static methods
  may now be called directly, so `SharedThreadPool.execute(...)` in place of
  `SharedThreadPool.INSTANCE.execute(...)` (although the later still works).
- Asynchronous `ListenerSupport` instances built using 
  `ListenerSupport#Builder.asynchronous` now use the `SharedThreadPool` rather than their
  own pool.
  
### Added
- The `SharedThreadPool` will now log an INFO message if a task runs longer than
  60 seconds to alert of tasks that possibly shouldn't be in the shared pool. That 
  default value can be customized via the 
  `common.sharedthreadpool.long_task_notify_ms` system property.


## [1.1.1] - 2017-10-31
### Fixed
- Greatly simplify logic in in `SingleThreadRepeatingTask` to resolve a possible
  race condition.
  
### Changed
- Deprecate many things in `IOKit`: `close` variations, `copy` variations, 
  `isBeingDeserialized()` and `DESERIALIZATION_HINT`. 
- Deprecate mutability methods of `Pair` and `Triple`. 
  **WARNING**: Serialization support will also be removed in 1.2.  


## [1.1.0] - 2016-11-02
### Added
- Add `Pair` and `Triple` classes.

### Changed
- Move `IOKit` from the `com.logicartisan.common.core.io` package to 
  `com.logicartisan.common.core`. 


## [1.0.0] - 2016-10-31
- Initial release, slimmed down from
  [StarLight Common](https://bitbucket.org/robeden/starlight-common).