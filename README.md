# trackunit
TrackUnit interview

## What is this?
This is a repository created as part of the interview process at TrackUnit.

## Requires clarification
### Hire manager OpenAPI specification
- `listHires` does not return a page as in the provided example, it returns a `Hire` according to the specification. I have reworked the schema to return a page matching the other endpoints.
- Pagination has no sorting. This can lead to a race condition where an already processed item can be seen on a future page (if, in the meantime, new data has been inserted or deleted).

### Trackunit API
- I do not ensure that `externalReference` is globally unique. We should probably get the server name from the `/v3/info` endpoint and prefix `externalReference` with that.

### Mappings
- `serialNumber` vs. `stockNumber` as `serialNumber` is not always expected to be available. Confer whether using `serialNumber` when available is preferable to always using `stockNumber` instead of using `stockNumber` as a fallback.
- Timestamp issue. Local date timestamps are ambiguous when setting the clock back. Are there expected to be any times during the night, if so can the API be improved?
- Customer type: I do not know the consequences of this mapping, but if flexible and as I assume it affects functionality in Trackunit, I would do this mapping together with the customer if possible.

## On the TODO list
- Move any filtering done in the mapper to the queries if the API supports it. Consider keeping filtering in mappers depending on how defensive we want to be.
