# "Posted today" means the detail endpoint's date equals today in America/Regina

The list endpoint only gives a relative label ("Posted Today", "Posted Yesterday", "Posted 30+ Days Ago") computed on Workday's side in the employer's timezone, so trusting it alone is off by up to a day around midnight. The detail endpoint gives an absolute `startDate` but costs one request per posting. We use the label as a prefilter (fetch detail only for postings labelled "Posted Today" or "Posted Yesterday") and count a posting as today's when its `startDate` equals the current date in `America/Regina`. This gives an exact answer at a cost of a few extra requests per Company rather than one per posting.

## Considered Options

- Trust the label alone: zero extra requests, wrong at day boundaries.
- Fetch detail for every posting: exact, but multiplies request volume by up to 20x.
- "First seen in this run": independent of Workday's dates, but the first run marks everything new and postings appearing between runs are missed until the next day. First-seen and last-seen are stored anyway so this can become a "New" badge later.
