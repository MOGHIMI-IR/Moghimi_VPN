# Privacy — Moghimi VPN

## Network identity lookup

After the VPN tunnel is reported active, Moghimi VPN requests the public IP address and an approximate country for the VPN egress address. The current implementation tries:

1. `https://api.country.is/`
2. `https://ipapi.co/json/` as a fallback if the first lookup fails.

The lookup is performed by the app after its own package has been included in the VPN tunnel. As a result, these providers should see the VPN egress address, not the user's original ISP address. This must still be verified on real devices and networks.

The providers receive the public IP address they observe and may process it under their own privacy policies. Country-level IP geolocation is approximate; it does not reveal a device's GPS location and can be inaccurate for VPN, mobile, or proxy exit addresses.

If both lookups fail, the app should continue showing the VPN connection state without inventing an IP or country.

## Site synchronization

The app reads active entries that the Moghimi website exposes through the Supabase Data API and Storage. It uses a public publishable key, not a service-role key. Row-level security and storage policies determine what anonymous clients may read.

The app does not need access to website admin accounts or private order data.

## VPN traffic

The VPN tunnel is established by Android's system VPN service after the user grants permission. The selected route determines where tunneled traffic exits. Supabase is used to retrieve route data; normal VPN traffic is not relayed through Supabase.

## Data minimization

- The app does not request GPS location for country display.
- The app does not need contacts, SMS, call logs, or accessibility access for its core VPN feature.
- Logs and diagnostics should not include raw route payloads, credentials, or signed storage URLs.

This document describes the current implementation intent. Verify actual behavior on a physical device and review provider policies before a public stable release.
