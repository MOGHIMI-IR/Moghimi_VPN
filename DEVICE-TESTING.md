# Moghimi VPN — Device QA Checklist

This checklist is for physical Android devices. A successful CI build does not replace these tests.

## Installation and first launch

- [ ] Install the ARM64 APK on a recent 64-bit Android device.
- [ ] Confirm the launcher name and icon say Moghimi VPN.
- [ ] Confirm the main screen does not expose engine or profile-format terminology.
- [ ] Confirm the dashboard scales correctly on a compact phone and a large phone.
- [ ] Confirm animations remain smooth when battery saver is enabled.
- [ ] Confirm Android's VPN consent sheet appears only when needed.

## Cloud routes and smart selection

- [ ] Launch with working internet and confirm active site routes arrive.
- [ ] Tap refresh twice quickly and confirm only one sync runs at a time.
- [ ] Test with one active route.
- [ ] Test with multiple routes with different real delay results.
- [ ] Confirm the fastest measured route is preferred.
- [ ] Test a route with an open TCP port but failed full tunnel connection.
- [ ] Confirm the next ranked route is attempted after a failed start.
- [ ] Disable all site routes and confirm the app reports no available route safely.
- [ ] Confirm an unsuccessful sync does not erase the last known working route list.

## Connected state and IP information

- [ ] Connect successfully and confirm Android's persistent VPN key/notification appears.
- [ ] Confirm the app's own network lookup is routed through the VPN.
- [ ] Compare the displayed public IP with a trusted IP-check page opened in the same device.
- [ ] Confirm the displayed country/flag matches the geolocation result, allowing for IP database inaccuracies.
- [ ] Test when the primary country lookup service is unavailable and confirm fallback behavior.
- [ ] Test when both IP lookup providers are unavailable; the app should still remain connected and show unknown information rather than a false location.
- [ ] Confirm ping is displayed in milliseconds and updates when a different route is selected.

## Disconnect, lifecycle and recovery

- [ ] Disconnect from the app and confirm Android's VPN indicator disappears.
- [ ] Revoke VPN permission in Android settings and confirm the app handles the state safely.
- [ ] Background and reopen the app while connected.
- [ ] Lock/unlock the phone and confirm the connection state remains accurate.
- [ ] Reboot the phone and confirm no unintended auto-connect occurs.
- [ ] Switch Wi-Fi to mobile data while connected.
- [ ] Test temporary network loss and recovery.
- [ ] Test switching routes manually while disconnected and while connected.

## Privacy and security

- [ ] Confirm only the public Supabase publishable key is packaged; no service-role key or admin secret.
- [ ] Confirm public IP lookup requests happen only after the VPN is active.
- [ ] Confirm the privacy notice explains the external IP geolocation providers.
- [ ] Confirm no unnecessary permissions have been introduced.
- [ ] Verify the APK signature and compare its SHA-256 checksum with the published file.
- [ ] Test the APK with Google Play Protect and reputable mobile security products; document results without assuming zero detections.

Record device model, Android version, network type, app version, selected route, ping, IP, and logs for each failure.
