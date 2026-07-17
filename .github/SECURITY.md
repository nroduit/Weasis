# Security Policy

Weasis is a desktop DICOM viewer (an Apache Felix OSGi / Swing application) used
in clinical and research settings for diagnostic imaging. Because it opens and
processes medical-imaging data that may contain sensitive health information
(PHI/PII) — from local files, DICOMDIRs, and remote DICOMWeb/DICOM network
sources — we take security issues seriously and appreciate responsible
disclosure.

## Supported Versions

Security fixes are provided for the latest released version. We recommend always
running the most recent release.

| Version | Supported          |
| ------- | ------------------ |
| 4.7.x   | :white_check_mark: |
| < 4.7   | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.**

Instead, report them privately using one of the following channels:

- **Preferred:** Open a [private security advisory](https://github.com/nroduit/Weasis/security/advisories/new)
  via GitHub's "Report a vulnerability" feature.
- Alternatively, email the maintainer at **nicolas.roduit@gmail.com**.

Please include as much of the following as you can to help us triage quickly:

- The type of issue (e.g. path traversal on import, exposure of PHI, XML
  external entity, deserialization, SSRF via DICOMWeb/WADO, TLS/certificate
  handling, insecure URL/protocol handling from `weasis://` launch parameters,
  code execution via a crafted DICOM object or plug-in, etc.).
- The affected component(s) and version (viewer / DICOM explorer, codec,
  DICOMWeb query-retrieve / send, RT, 3D, SR, acquisition/dicomizer, launcher,
  etc.).
- Step-by-step instructions to reproduce the issue.
- Proof-of-concept or exploit code, if available.
- The impact, including how an attacker might exploit it.

**Do not include real patient data** in your report. Use synthetic or fully
anonymized DICOM data only.

## Disclosure Process

- We will acknowledge receipt of your report within **5 business days**.
- We will investigate and provide an initial assessment within **10 business
  days**, and keep you informed of progress toward a fix.
- Once a fix is available, we will coordinate a release and a public advisory.
  We are happy to credit you in the advisory unless you prefer to remain
  anonymous.

We ask that you give us a reasonable amount of time to address the issue before
any public disclosure.

## Scope

This policy covers the Weasis application and its source code in this repository.
Vulnerabilities in third-party dependencies (dcm4che / weasis-dicom-tools,
OpenCV, Apache Felix, FlatLaf, JOGL, etc.) should be reported to the respective
upstream projects; if a dependency issue affects Weasis, feel free to let us
know so we can update.

Thank you for helping keep Weasis and its users safe.
