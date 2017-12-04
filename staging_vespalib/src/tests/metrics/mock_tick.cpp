// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "mock_tick.h"

namespace vespalib::metrics {

TimeStamp
MockTick::next(TimeStamp prev)
{
    std::unique_lock<std::mutex> locker(_lock);
    _prevValue = prev;
    _blocked.store(true);
    _blockedCond.notify_all();
    while (_runFlag && !_provided) {
        _providedCond.wait(locker);
    }
    _blocked.store(false);
    if (_provided) {
        _provided.store(false);
        return _nextValue;
    } else {
        // killed
        return TimeStamp(0);
    }
}

void
MockTick::kill()
{
    std::unique_lock<std::mutex> locker(_lock);
    _runFlag.store(false);
    _blockedCond.notify_all();
    _providedCond.notify_all();
}

void
MockTick::provide(TimeStamp value)
{
    std::unique_lock<std::mutex> locker(_lock);
    _nextValue = value;
    _blocked.store(false);
    _provided.store(true);
    _providedCond.notify_all();
}

TimeStamp
MockTick::waitUntilBlocked()
{
    std::unique_lock<std::mutex> locker(_lock);
    while (_runFlag && !_blocked) {
        _blockedCond.wait(locker);
    }
    if (_blocked) {
        return _prevValue;
    } else {
        // killed
        return TimeStamp(0);
    }
}

MockTick::MockTick()
    : _lock(),
      _runFlag(true),
      _provided(false),
      _blocked(false),
      _providedCond(),
      _blockedCond(),
      _nextValue(0.0),
      _prevValue(0.0)
{}

} // namespace vespalib::metrics
