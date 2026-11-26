## How to run it
### Requirements
- Java 17+

### Dry-run mode against embedded WireMock stub
The local profile starts an embedded WireMock server on http://localhost:8089 serving stubbed responses from src/main/resources/wiremock/.

You need a `application-local.properties` file next to `application.properties` in `resources/`. I am running my tests with these properties in it:
```
hire-manager.username=username
hire-manager.password=password
wiremock.port=8089
hire-manager.base-url=http://localhost:8089
```

### Powershell (I am on the go, and only had a Windows laptop with me)
`./gradlew bootRun --args="--spring.profiles.active=local --dry-run"
`

Running this will perform a full synchronization pass. There is no implementation of incremental updates.

## What I built
A full synchronization dry-run version of the connector. No incremental updates, no test coverage or handling of any errors when calling the service. It paginates the hire manager (it pulls an authorization token for this, which is not yet cached) for machines, hires and accounts. It then maps this data to `SynchronizationRequest` and writes it to a file.

## Decisions I made and why
### What counts as an asset and a contract
- Assets: Machines are the base entity that count as an asset given the correct circumstances (e.g. only `fleet == HIRE`).
- Contracts: `Hire` and `HireLine`: Only `kind == HIRE` with `state != CANCELLED` are mapped. We also filter `INTERNAL` customer accounts.
- Namespaced external references: `externalReference` should be globally unique, so in production we should prefix these with e.g. `serverName` from `/v3/info` to be robust against a customer having multiple hiring manager installations.

### Asset status rule
As `machine.state` is manually set (and explicitly stated as not being derived from hire data), we instead derive status from hire data. See `HireToRentalMapper::deriveAssetStatus`.

### Time zones and timestamps
- We combine line-level dates/times, and fallback to hire-level dates/times when missing data, and use defaults when times are omitted.
- During the daylight savings autumn fallback, there is a problem with local date times as these timestamps become ambiguous. ZonedDateTime resolves this deterministically to the earlier offset.

### Incremental sync strategy
I did not have time to consider this part, other than storing a sync time and using this in the future requests.

### Chosen Trackunit headers
- `X-Update-Strategy: REPLACE`. Ensures cancelled, returned or deleted hires are cleanly replaced on the asset.
- `X-Validate-Overlaping-Contract-Items: false`. It is valid ERP data to have overlapping hires for the same machine.
- `X-Validate-Asset-Exists: false`: Allows Trackunit to return 202 Accepted if the asset hasn't been added yet.
- `X-Validate-Rental-Period: true`. Reject corrupt periods. Ideally we would also validate this internally so avoid sending it in the first place.

### How it runs
A one-shot CLI/scheduled job. Could be triggered by a CronJob or a Spring `@Scheduled` outside of the maintenance window mentioned. More thought would be put into the incremental updates depending on the requirements for how up-to-date data must be.

## Assumptions about the vendor I could not verify and my top 5 questions for the vendor's engineers

- Does line edits update `hire.modifiedAt`? Relevant to implementation of incremental updates.
- Does the hire manager system store `Europe/London` wall-clock time (including BST in summer), or fixed UTC year round? In particular, how are timestamps during the daylight time savings periods?
- Pagination consistency: List endpoints do not provide any sort parameter and the ordering is unspecified. What is the default sort order on `/v3/hires`, `/v3/accounts` and `/v3/machines`? And can records shift during concurrent changes?
- Deleted hires disappear from `/v3/hires`. Is there any way to detect this other than a periodic full sync?
- Can a machine's `stockNumber` ever change? Since it is a freetext field in some cases, it is interesting to know if they can change.

## What I cut, and why
- Any network and error handling from calling the service. Within the time frame that was not realistic for me, and with extra work in setting up testing for this it was the first thing to cut.
- Proper token handling and caching. There are libraries that can be pulled in to handle this for production.
- Incremental state updates and scheduling. Not enough time to start looking at this for real, and it seemed to make more sense to look at the full synchronization first.

## Things in the spec that surprised me
- The broken `GET /v3/hires/` response schema. Paging vs. a single hire returned. I patched this assuming it should also be a page, and continued working from that.
- The validation header is spelled `X-Validate-Overlaping-Contract-Items` with a missing `p`.
- Low rate limit meaning that large customers must paginate carefully.

## Testing: what I tested and why
- A few simple setups in the WireMock. Mostly the positive case as I ran out of time to do more testing, including automatic tests.

## How I used AI (tools, what for, what you changed or threw away)
- I used it for boilerplate and scaffolding work, e.g. laying out classes that I then fill out or edit.
- It helped quite a bit with Gradle for generating the classes from the OpenAPI specifications as I have never had to do that before.
- It was used to generate the initial wiremock files for testing.
- I generally use prompts to ping-pong ideas and solutions as well. For example it would lay out a fully fledged `TokenInterceptor`, where it would implement the concurrency mechanisms directly instead of using existing resources designed for it. In those cases I reign it back for a project like this.

## If I had 2 more days / 2 more weeks
I think the timescale is a bit off from my assignment, but with a bit more time I would have added some automatic tests as the first thing.

Next, I think the most interesting problem would be the incremental updates. After that I would have looked into making the API calls robust against a closer to real service.

And on the long haul (2 weeks) I would play with observability, i.e. exposing some Prometheus metrics for last successful run, how many records skipped and so on.

## Time spent
I think about 4-5 hours or so on the code part. It took some time to lay out the boilerplate of the initial project as I have not had to do that for a while. I also did this on a non-developer machine where I had to set up everything from scratch (IDE, Git, etc.).

I spent a good hour on top of that writing this README.
