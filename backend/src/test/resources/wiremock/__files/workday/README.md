# Recorded Workday responses

Captured on 2026-09-23 from NVIDIA's Career Site (`nvidia.wd5.myworkdayjobs.com/NVIDIAExternalCareerSite`)
with `POST /wday/cxs/nvidia/NVIDIAExternalCareerSite/jobs`, body `{"appliedFacets":{},"limit":20,"offset":N,"searchText":""}`,
headers `Content-Type: application/json` and `Accept-Language: en-US` (ADR-0001). The `facets` block was
removed; everything the scraper reads is intact.

| File | Offset | What it shows |
| --- | --- | --- |
| `nvidia-jobs-page-1.json` | 0 | `total` is 2000, the endpoint's cap: this Company is truncated. 20 postings. |
| `nvidia-jobs-page-2.json` | 20 | `total` is 0 on every page after the first, so it must be read from the first page only. |

Tests that need a particular `total` or page length build pages in the same shape; see `ScrapeRunTest`.
