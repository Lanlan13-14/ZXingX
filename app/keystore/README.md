# ZXingX release keystore

Open-source demo signing key used by `:app:assembleRelease` and GitHub Actions.

| Field | Value |
| --- | --- |
| File | `zxingx-release.jks` |
| Alias | `zxingx` |
| Store / key password | `zxingx-open-source` |

This key is intentionally public so CI and local release builds share one signature.  
Do **not** reuse it for a Play Store production app.

Certificate SHA-256:

```text
17:2C:1D:3F:3C:93:D4:9C:8A:11:C6:3E:C1:CB:2B:93:EF:5D:3D:80:D0:33:7E:5F:A4:EB:00:4C:93:6A:FE:6A
```
