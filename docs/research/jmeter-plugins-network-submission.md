# JMeter Plugins Network Submission Research

## Overview

jmeter-plugins.org allows third-party plugins to be distributed via the JMeter Plugins Manager
without contributing source code to their repository. You maintain your own project and use JPGC
as distribution infrastructure.

## Submission Process

1. Raise the topic on the jmeter-plugins community forum for detailed instructions
2. Prepare a plugin descriptor JSON entry (see format below)
3. Submit a PR to https://github.com/undera/jmeter-plugins adding your entry to `site/dat/repo/various.json`

## Plugin Descriptor Format

Each plugin is a JSON object in an array. Required fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | String | Unique plugin identifier (e.g. `"loadmagic-load-distributor"`) |
| `name` | String | Display name (e.g. `"Distributed Load Distributor"`) |
| `description` | String | Usage guide / description |
| `screenshotUrl` | String | URL to a screenshot |
| `helpUrl` | String | URL to help page (e.g. GitHub repo) |
| `vendor` | String | Vendor name (e.g. `"LoadMagic"`) |
| `markerClass` | String | Class whose presence indicates the plugin is installed |
| `componentClasses` | Array | Significant classes (Elements and GUIs) |
| `versions` | Object | Map of version numbers to version descriptors |

### Version Descriptor Fields

| Field | Required | Type | Description |
|-------|----------|------|-------------|
| `downloadUrl` | Yes | String | URL to download the JAR |
| `changes` | No | String | Changelog for this version |
| `libs` | No | Object | Map of dependency library names to download URLs |
| `depends` | No | Array | Array of plugin ID dependencies |

Optional top-level fields:
- `installerClass` (String) — class with `public static void main(String[])` run during install
- `canUninstall` (Boolean) — defaults to `true`

## Draft Descriptor for Load Distributor

```json
{
  "id": "loadmagic-load-distributor",
  "name": "Distributed Load Distributor",
  "description": "Automatically distributes thread counts across JMeter generators for distributed testing. Set your total desired load once, specify the number of generators at runtime (-Jgenerator.id and -Jgenerator.count), and each generator runs exactly its fair share. Auto-activates without requiring a test plan element. Supports Standard ThreadGroup, Ultimate Thread Group, Concurrency Thread Group, and Stepping Thread Group.",
  "screenshotUrl": "",
  "helpUrl": "https://github.com/loadmagic/jmeter-load-distributor",
  "vendor": "LoadMagic",
  "markerClass": "ai.loadmagic.jmeter.distributed.LoadDistributor",
  "componentClasses": [
    "ai.loadmagic.jmeter.distributed.LoadDistributor",
    "ai.loadmagic.jmeter.distributed.LoadDistributorGui",
    "ai.loadmagic.jmeter.distributed.LoadDistributorAutoActivator"
  ],
  "versions": {
    "1.1.0": {
      "downloadUrl": "https://github.com/loadmagic/jmeter-load-distributor/releases/download/v1.1.0/jmeter-load-distributor-1.1.0.jar",
      "changes": "Auto-activation via Function SPI — no test plan element required. Backward compatible with v1.0.0.",
      "depends": ["jmeter-core"]
    },
    "1.0.0": {
      "downloadUrl": "https://github.com/loadmagic/jmeter-load-distributor/releases/download/v1.0.0/jmeter-load-distributor-1.0.0.jar",
      "changes": "Initial release. Requires Config Element in test plan."
    }
  }
}
```

## TODO Before Submission

- [ ] Add a screenshot to the repo (the GUI Config Element panel, or a terminal showing the distribution log output)
- [ ] Fill `screenshotUrl` with raw GitHub URL to the screenshot
- [ ] Raise topic on jmeter-plugins community forum
- [ ] Submit PR to `undera/jmeter-plugins` adding entry to `site/dat/repo/various.json`

## References

- Plugin descriptor format: https://github.com/undera/jmeter-plugins/blob/master/site/dat/wiki/PluginRepositoryDescriptorFormat.wiki
- Third-party plugin examples: https://github.com/undera/jmeter-plugins/blob/master/site/dat/repo/various.json
- Plugins Manager wiki: https://github.com/undera/jmeter-plugins/blob/master/site/dat/wiki/PluginsManager.wiki
- Plugins Manager repo: https://github.com/undera/jmeter-plugins-manager
