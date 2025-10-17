# Raw Resource Files

The Android client bundles two plain-text datasets under `app/src/main/res/raw/` that are
consumed when building the VPN runtime configuration:

- `ip.txt` – A newline-delimited list of IPv4 network ranges in CIDR notation. The VPN uses
  the list as its bypass (direct-connect) table when constructing `VPNLinkConfiguration` in
  `MainActivity`. The `RawReader` helper loads the entire file into the `BypassIpList`
  property so traffic targeting those networks can avoid proxying.
- `domain.txt` – A newline-delimited domain rule set that associates host names with the
  resolver source `/223.5.5.5/nic`. During VPN configuration the file is read into
  `DNSRuleList`, supplying domain-based routing rules for the embedded DNS engine so DNS
  queries can follow the curated policy instead of relying on upstream discovery.

Both files are now loaded through `SettingsRepository`, which caches the bundled content and
checks for user overrides saved under the app's internal storage (`user_bypass_ip.txt` and
`user_dns_rules.txt`). The settings screen exposes "Bypass IP list" and "Domain rules"
entries that write those overrides, allowing the Android client to match the Linux build's
ability to edit routing datasets. When an override is cleared the repository falls back to
the packaged resources. Keeping the base data in `res/raw` lets Android package the lists
efficiently while making runtime access straightforward.
