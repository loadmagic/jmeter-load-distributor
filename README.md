# JMeter Distributed Load Throttle

Automatically distributes thread counts across JMeter generators for distributed testing. Set your total desired load once, specify the number of generators at runtime, and each generator runs exactly its fair share.

## The Problem

In JMeter distributed testing, every generator runs the **full** thread count from the test plan:

- Test plan says 100 threads
- You start 4 generators
- Each runs 100 threads = **400 total** (not 100)

To get 100 total, you manually calculate 25 threads per generator and edit the test plan. If you add or remove a generator, you recalculate and edit again.

## The Solution

Drop this plugin into your test plan. At runtime, tell each generator its ID and the total count:

```bash
# Generator 1 of 3
jmeter -Jgenerator.id=1 -Jgenerator.count=3 -n -t test.jmx

# Generator 2 of 3
jmeter -Jgenerator.id=2 -Jgenerator.count=3 -n -t test.jmx

# Generator 3 of 3
jmeter -Jgenerator.id=3 -Jgenerator.count=3 -n -t test.jmx
```

Each generator automatically calculates its share. The test plan stays unchanged.

## How It Works

The plugin runs at test start (before any threads are created) and adjusts each ThreadGroup's thread count:

```
Test plan: 100 threads, 3 generators

Generator 1: 34 threads
Generator 2: 33 threads
Generator 3: 33 threads
Total:       100 (exact)
```

Remainder threads are distributed to lower-numbered generators. The sum across all generators **always equals the original total** — no rounding errors, no lost threads.

### More Generators Than Threads

```
Test plan: 4 threads, 5 generators

Generator 1: 1 thread
Generator 2: 1 thread
Generator 3: 1 thread
Generator 4: 1 thread
Generator 5: 0 threads (sits this one out)
```

## Supported Thread Groups

| Thread Group | Support |
|---|---|
| **Standard ThreadGroup** | Full — thread count distributed |
| **Ultimate Thread Group** | Full — each schedule row distributed independently |
| **Concurrency Thread Group** | Full — target level distributed |
| **Stepping Thread Group** | Full — thread count distributed |

### Ultimate Thread Group Example

```
Schedule (3 generators):
  Row 1: 50 threads, 30s ramp, 120s hold
  Row 2: 100 threads, 60s ramp, 300s hold

Generator 2 gets:
  Row 1: 17 threads, 30s ramp, 120s hold
  Row 2: 33 threads, 60s ramp, 300s hold

Timings unchanged — only thread counts divided.
```

## Installation

1. Download `jmeter-load-throttle-1.0.0.jar` from [Releases](https://github.com/loadmagic/jmeter-load-throttle/releases)
2. Copy to `<jmeter>/lib/ext/`
3. Restart JMeter

Or install via JMeter Plugins Manager (coming soon).

## Usage

1. Open your test plan in JMeter
2. Right-click **Test Plan** → **Add** → **Config Element** → **Distributed Load Throttler**
3. Save the test plan
4. Run each generator with `-Jgenerator.id=N -Jgenerator.count=TOTAL`

### Without the Properties

If you run without `-Jgenerator.id` and `-Jgenerator.count`, the plugin does nothing — your test runs at full load. This means the same .jmx works for both single-node and distributed testing.

## Non-Destructive

Thread count modifications are **in-memory only**. JMeter's running version mechanism automatically restores original values when the test ends. Your .jmx file is never modified.

## Building from Source

```bash
mvn clean package
```

The JAR is at `target/jmeter-load-throttle-1.0.0.jar`.

## License

Apache License 2.0 — same as JMeter.

## Author

[LoadMagic](https://loadmagic.ai) — AI-powered performance testing.
