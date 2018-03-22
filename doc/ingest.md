## Ingesting Documents

Overview's unit of analysis is a "Document," but the user uploads "Files." How
do we convert one to the other?

**What's a Document?**

A single Document is Overview's unit of analysis, presented to the user. It has:


* `text`: valid UTF-8 with a character-count limit, stored in Postgres.
* `pdf`: valid PDF, optimized to be viewed quickly, stored in S3 or a file.
* `thumbnail`: valid PNG or JPEG image (optional).
* `metadata`: JSON Object, supplied by the user and supplemented by Overview's
  ingest pipeline steps.

**What's a File?**

A File is input provided by the user.

But let's add **_derived_ Files** to the mix: data of the same "shape" as a
File, produced by running some processing **Steps** on a user-input File or
previously- derived File. (A "Step" is a single operation like "unzip", "OCR"
or "convert to PDF.")

A File (user-input or derived) has:

* `blob`: binary data, stored in S3 or on disk.
* `metadata`: JSON Object, supplied by the user and optionally supplemented by
  Overview's processing pipeline.
* `pipelineOptions`: user-supplied (and Step-augmented) options to be consulted
  during processing. For instance: `ocr` (Boolean) determines whether to run
  OCR on a file.
* `filename`: the filename of a user-input file, or the "derived" filename of
  a derived File: for instance, if the input `filename` is `Archive.zip`, the
  `filename` of a derived File might be `Archive.zip/report.pdf`.
* `contentType`: what one would see in an HTTP `Content-Type` header. Overview
  stores all user input as `application/octet-stream`, but some derived files
  will have known values, such as `application/pdf`.
* `text`, `thumbnail`: (derived Files only) supplemental data about the File,
  used to create Documents.
* `processingError`: optional error message that should be presented to the
  user, describing why the user input is invalid or how Overview is broken.
* `nChildren`: number of _derived_ Files produced by processing this File in a
  single Step.
* `parentId`, `rootId`: `null` for user-input Files. As for derived files: let's
  look at the tree structure.

# Concept: Files form Trees

A **Step** runs on a single File and produces zero, one or many **children**
(derived Files). For example, if the user uploads `Archive.zip` and
`report.docx`, the trees might look like this:

* `Archive.zip`
  * `subarchive.tar.gz`
    * `stats.txt` (unprocessed)
      * **`stats.txt`** (`application/pdf` with `text` and `thumbnail`)
    * `summary.pdf` (unprocessed)
      * **`summary.pdf`** (`application/pdf` with `text` and `thumbnail`)
    * **`run.bin`** (fails to process: `unhandled`)
  * `instructions.docx` (unprocessed)
    * **`instructions.docx`** (`application/pdf` with `text` and `thumbanil`)
* `screenshot.png`
  * **`screenshot.png`** (`application/pdf` with `text` and `thumbanil`)

Files in **bold** will appear in Overview as Documents and Errors.

Overview creates a (user-visible) Document from leaf Files that are
`application/pdf` with `text` and `thumbnail`. Overview creates Errors from all
Files with `processingError` set. Overview ignores all other Files. (These
decisions come at the "ingest" phase of the pipeline.)

# Concept: A File is a State Machine (created; written; processed; ingested)

Processing a single Zipfile full of PDFs that need OCR can take hours (or days).
Overview is built to crash: it runs on laptops that sometimes lose power, and it
runs on the cloud with frequent deploys. It would be unacceptable to restart
processing of a user-input file from the beginning. Furthermore, Overview may
**stop during any write**: its expects `SIGKILL` at any time. On start, Overview
must pick up where it left off: it must **reload state from the database**.

The solution: convert a single File to a Tree to Documents and Errors through
**atomic state transitions**. With each transition, Overview makes more data
**immutable**.

To make the _Tree_ a state machine, we make each _File_ a state machine:

1. A File is **created**. Think of a "created" File as an open file handle on
   disk. Its `blob`, `thumbnail` and `text` may be set or unset. (Once they
   are set, we ignore attempts to set them again.)
2. The File transitions to **written**. At this point, `blob`, `thumbnail` and
   `text` are immutable and the File is ready to be processed by a **Step**.
3. A Step sets `processingError` and `nChildren`, and the File transitions to
   **processed**. A Processed file will never be processed by a Step again.
4. Documents and Errors are created, and the File transitions to **ingested**.
   An ingested file has reached its terminal state: it should disappear from
   the pipeline's memory.

# Concept: Step as unit of work

Finally, we have a definition for **Step**:

**A Step operates on a `written` File: it invokes a sequence of atomic database
writes that creates 0, 1 or many `written` and `processed` children. Finally, it
outputs its input as a `processed` File.**

(Generally, a Step outputs _written_ children; but if it outputs a child that is
`application/pdf` with `text` and `thumbnail`, Overview considers the child
_processed_.)

One last definition: a **Step worker** is a single process that continually
runs a Step. For instance, an "unzip" worker waits for files to unzip and then
unzips them, one at a time. If there are two "unzip" workers, then Overview can
unzip two files simultaneously.

# Concept: The Tree is a State Machine, too

Certain rules follow logically:

* **A Step operates on a `written` File** (converting it to processed).
* **Steps can recurse** and **multiple workers can run a Step concurrently
  within the same File tree.** For instance, unzip might run on `archive.zip`,
  outputting `archive.zip/subarchive.zip`, which is fed to a second "unzip"
  Step Worker which runs at the same time as the first.
* While the Step runs, a written File may have zero or more children;
  **children may have any state**. For instance, `archive.zip`'s "unzip" Step
  might output `archive.zip/summary.pdf`, which might be completely ingested
  before the "unzip" step finishes.
* During resume, a Step may run on a written File that already has already
  output children. When the Step outputs those children, Overview must ignore
  them: **a Step cannot overwrite children**, because they are immutable.
* A **Step must be deterministic**: if it's run multiple times on the same
  input, it must produce the exact same children and optional error message, in
  the exact same order.
* Within a Tree, **written Files transition to processed in nondeterministic
  order**.
* The pipeline forgets ingested files. **If a File is ingested, all its
  children are ingested.**

Basically: when Overview restarts, it may "replay" certain operations: it may
unzip a file that was mostly-unzipped already, for instance. **Replayed
database writes must be no-ops.** In-memory, things are different: replaying
an unzip should make Overview track child Files' states.

# Concept: Scaling with Streams

Overview's ingest pipeline is often idle and often backlogged. To scale, we want
to **spin up workers per-Step, on demand**.

"On demand" here means _database_ demand. We can't launch infinite workers,
because we can't handle infinite concurrent database writes. **The bottleneck is
the database**: the perfect number of workers is exactly enough to keep us
writing to the database as quickly as possible.

In other words: **the ingest phase dictates demand**. It should ask the rest of
the pipeline to produce as many processed Files as it can handle. From there,
scaling is easy: if a Step's workers are operating at 100% CPU for a long time,
we know the ingest phase _isn't_ writing to the database as quickly as it can:
we can spin up more workers.

Conversely, we don't want to scale too high. Step workers connect to Overview
over HTTP. Overview reduces their CPU load by only reading from their TCP sockets
when there's demand. When there's no demand, the workers' HTTP POSTs stall,
reducing their CPU usage. Low CPU usage means can shut down workers.

Overview uses
[Akka Streams](https://doc.akka.io/docs/akka/current/stream/stream-introduction.html)
to build its on-demand pipeline. The concepts are universal, though: a stream
system like [Apache Kafka](https://kafka.apache.org/) could serve the same
purpose.

By the way: these flow diagrams are event-driven. Conceptually, the whole system
is evented and single-threaded.

## File Flows

Streams are demand-driven, and the demand comes from the ingest phase. So let's
sketch Overview's design, backwards:

# Ingest

                    ╭─────────╮
    ProcessedFile ▶─┤ Ingester├─▶ IngestedFile
                    ╰─────────╯

[Ingester](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/ingest/Ingester.scala)
demands processed Files. They can come in any order; though as we'll see later,
the pipeline makes a best effort to finish processing a File Tree soon after
beginning it.

ProcessedFiles have `id`, `parentId`, `rootId` and `nChildren`. From these,
Ingester infers enough Tree information to detect leaf ProcessedFiles and
parent ProcessedFiles whose leaves have all been ingested. It "ingests" them:
creates Documents and Errors and marks them as "ingested."

Then it asks for more.

# Process

                   ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                   ┃  ╭───────╮      ╭────────╮ WrittenFile ┃
    WrittenFile ━>━╉─>┤ merge ├<─────┤ buffer ├<──────╮     ┃
                   ┃  ╰───┬───╯      ╰────────╯       │     ┃
                   ┃      │   ╭───────────────╮   ╭───┴───╮ ┃
                   ┃      ╰──>┤ StepProcessor ├──>┤ split ├>╊━>━ProcessedFile
                   ┃          ╰───────────────╯   ╰───────╯ ┃
                   ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

[Processor](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/process/Processor.scala)
demands written Files and outputs processed Files.

The crux of its logic is in StepProcessor, which we'll see next.

As we know, a Step consumes a written File and outputs one or more written
and/or processed Files. (It always outputs its input as processed.) Processor
splits the output into two streams: processed Files are destined for the
Ingester, and written Files are fed back (recursively) to the processor.

**Step Processor**

                   ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                   ┃                ╭──────╮              ┃
                   ┃             ╭─>┤ Step ├>╮            ┃
                   ┃             │  ╰──────╯ │            ┃
                   ┃  ╭─────────╮│  ╭──────╮ │ ╭───────╮  ┃    WrittenFile or
    WrittenFile ━>━╉─>┤ Decider ╞╡─>┤ Step ├>╞═╡ merge ├─>╊━>━ ProcessedFile
                   ┃  ╰─────────╯│  ╰──────╯ │ ╰───────╯  ┃    ("ConvertOutputElement")
                   ┃             │  ╭──────╮ │            ┃
                   ┃             ╰─>┤ Step ├>╯            ┃
                   ┃                ╰──────╯              ┃
                   ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

[StepProcessor](https://github.com/overview/overview-server/tree/master/worker/src/main/boilerplate/com/overviewdocs/ingest/process/StepProcessor.scala.template)
demands written Files and outputs written _or_ processed Files. (We call these
outputs [ConvertOutputElements](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/model/ConvertOutputElement.scala)
in our code.)

[Decider](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/process/Decider.scala)
runs MIME-detection on the input to decide which Step applies. (It holds a
hard-coded registry of Steps.) It outputs each file to the correct Step's input.

[Step](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/process/Step.scala)
runs logic specific to its file type. For instance, "unzip" produces
written-File children and then its input File, as processed.

**Step Worker**

                   ┏━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┓
                   ┃ ╭───────╮          ╭──────────╮ ╭───────╮ ┃    WrittenFile or
    WrittenFile ━>━╉>┤ retry ├────────>─┤ HttpStep │ │ merge ├>╊━>━ ProcessedFile
                   ┃ ╰──┬────╯          ╰────────╥─╯ ╰──╥────╯ ┃    ("ConvertOutputElement")
                   ┃    ╎                ╭───────╨╮     ║      ┃
           POST ━>━╉─StepOutputFragment─>┤ worker ├───>─╢      ┃
                   ┃    ╎                ╰─┬─────╥╯     ║      ┃
                   ┃    ╎                ╭─┴─────╨╮     ║      ┃
           POST ━>━╉─StepOutputFragment─>┤ worker ├───>─╢      ┃
                   ┃    ╎                ╰─┬─────╥╯     ║      ┃
                   ┃    ╎                ╭─┴─────╨╮     ║      ┃
           POST ━>━╉─StepOutputFragment─>┤ worker ├───>─╜      ┃
                   ┃    ╎                ╰─┬──────╯            ┃
                   ┃    ╰╌<╌╌╌(timeout)╌╌<╌╯                   ┃
                   ┃         WrittenFile                       ┃
                   ┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛

[HttpStep](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/process/HttpStep.scala)
maintains a rather complex state. This diagram is reductive.

Here are the details:

* A [WrittenFile](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/models/WrittenFile.scala)
  input is a unit of work: to convert one WrittenFile into one or many
  ConvertOutputElements.
* A **worker** is a simple HTTP client. It loops:
  1. Demand fresh work (a `WrittenFile`)
  2. Download its `blob`
  3. Derive and upload [StepOutputFragments](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/process/StepOutputFragment.scala): atomic operations such as "create new child"; "update progress"; "write blob on current child"; and finally, "done" or "error".
* `HttpStep`'s [Workers](https://github.com/overview/overview-server/tree/master/worker/src/main/scala/com/overviewdocs/ingest/process/Worker.scala)
  are server-side representations of workers' states. They make sure each worker
  only sends a single HTTP POST at a time. They stream "fragments" from the
  workers, executing the database writes they represent, and output
  `ConvertOutputElement`s in the process. `Worker` is the part of Overview
  that demands TCP messages from the workers; when Overview's pipeline is at
  capacity, `Worker` will stop reading from the HTTP client -- temporarily
  stalling it so the ingest phase can catch up.
* A `Worker` can time out. That _always_ means the client has been killed
  (intentionally) and that another worker must start the same work from the
  beginning.
* The following are the _only_ ways for a Step to stop work on a WrittenFile:
  * A worker sends a `done` fragment
  * A worker sends an `error` fragment
  * The user _cancels_ processing of the WrittenFile, and then a worker sends
    an HTTP request for it.

## Cancel

In Overview, a user-triggered "cancel" is completely ordinary. The File-Tree
state machine follows the same rules as always.

"Cancel" allows Overview to speed up import by using alternative Step logic:
"output a `ProcessedFile` immediately with a `processingError` of `"canceled"`.

A user-triggered "cancel" is _advisory_: Overview may choose not to use the
alternative logic. (The ambiguity makes it easy to avoid races.)

Overview checks for cancellation in two places:

* In `HttpStep`, after `retry`: Overview may skip assigning a worker to a
  WrittenFile, opting instead to complete the conversion itself.
* In `HttpStep`, when a `worker` sends any request concerning its work: Overview
  completes the conversion and responds with `404`, which the worker should
  interpret to mean: "stop working on this task."

A long-running worker task should send period `HEAD` requests or `POST`
`progress` fragments. If Overview replies with `404 Not Found`, the worker
should end processing of its task. (This pattern doubles as a heartbeat: it
defers Overview's worker timeout.)

## Progress Reporting

While converting "root" WrittenFiles to Overview documents, the user expects to
know: how much work is done, and how much remains?

The answer is a number between `0.0` and `1.0`. It's always an estimate.

Each Step has a hard-coded `progressWeight`.

The "generate thumbnails" Step (along with any other terminal Step) has
`progressWeight=1.0`, meaning: "when this Step is done, processing of the
input WrittenFile is finished."

The "unzip" Step is more pessimistic. It has `progressWeight=0.1`, meaning:
"when this Step is done, 90 percent of processing is still unfinished."

While a Step runs, it generates `progress` fragments, from `0.0` to `1.0`.
Those progress fragments determine how much of the input WrittenFile has
been processed. When the WrittenFile is output as a ProcessedFile, Overview
considers its `progress` to be `1.0` -- multiplied by `progressWeight`.

The Step's _output_ WrittenFiles will be processed in subsequent Steps. They
together account for `1.0 - progressWeight` of the input file's `progress`.
Overview generates a WrittenFile when it receives a `n.json` fragment (where
`n>0`) or `done`; so Steps should be sure to send `progress` fragments
immediately before `n.json` fragments. That way, Overview will know how much
of the remaining progress to assign to that WrittenFile2.

In this way, each Step outputs `progress` fragments from `0.0` to `1.0`. Each
Step contributes a slice of progress itself and allocates the rest of the
progress to its children (recursively). Overview sums up the progress of each
slice to determine the progress of the entire import job. (Optimization:
Overview maintains a these progress slices in a
[RangeSet](http://google.github.io/guava/releases/snapshot/api/docs/com/google/common/collect/RangeSet.html#canonical-com.google.common.collect.DiscreteDomain)
instead of summing from scratch each time it reports progress.)
