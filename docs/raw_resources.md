# Raw Resource Files

The Android client bundles two plain-text datasets under `app/src/main/res/raw/` that are
consumed when building the VPN runtime configuration:

- `ip.txt` – A newline-delimited list of IPv4 network ranges in CIDR notation. The VPN uses
  the list as its bypass (direct-connect) table when constructing `VPNLinkConfiguration` in
  `MainActivity`. The `RawReader` helper loads the entire file into the `BypassIpList`
  property so traffic targeting those networks can avoid proxying.
- `domain.txt` – A newline-delimited domain rule set that associates host names with the
  resolver source `/223.5.5.5/nic`. During VPN configuration the file is read into
  `DNSRuleList`, supplying domain-based routing rules for the embedded DNS engine.

Both files are read at connect time through `RawReader.readRawResource` in
`MainActivity` (see the `BypassIpList` and `DNSRuleList` assignments). Keeping the data in
`res/raw` allows Android's resource system to package the lists efficiently while making
runtime access straightforward. Even when the "proxy all traffic" preference is enabled we
continue to load the domain rules so the embedded resolver keeps using the curated rule
set instead of falling back to slower upstream discovery.
