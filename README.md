# Deployment
## Local
To start the functions locally on your laptop, run:  
```
gradle jar --info && gradle azureFunctionsRun
```

The local azure function will be listening on port 7071, but when you do `ctrl-c` it doesn't actually kill the program.  
Therefore, make sure to kill the port between local sessions.  You can easily do this by adding the following to your `~/.zshrc` file
```zsh
# add these to your .zshrc
alias killport='f() { lsof -nPi -sTCP:LISTEN | grep $1 | awk '\''{print $2}'\'' | xargs kill -9; }; f'
alias killAzureFunctionsLocal="killport 7071"
```

Then you can kill the program simply by running `killAzureFunctionsLocal`

#### Local Debugging
You can debug locally through intellij by listening to localhost on port 5005

#### Settings.local.json
You need to have a `local.settings.json` file similar to
```json
{
  "IsEncrypted": false,
  "Values": {
    // Azure storage connection string to store state of durable function.  Fill in *AccountName*
    "AzureWebJobsStorage": "",

    "AzureConfigurationStage": "TEST",

    // fill in info for the Test azure storage account 
    "AzureStorage.Test.AccountKey": "<>",
    "AzureStorage.Test.AccountName": "<>",

    // this info comes from the document intelligence endpoint in your azure account
    "DocumentIntelligence.Test.ApiEndpoint": "https://eastus.api.cognitive.microsoft.com/",
    "DocumentIntelligence.Test.ApiKey": "",
    "DocumentIntelligence.Test.ClassifierModel": "",
    "DocumentIntelligence.Test.ExtractorModel": "",
    "DocumentIntelligence.Test.CheckExtractorModel": "",

    "Test.NumWorkers": "4",
    "Prod.NumWorkers": "15",

    "AzureStorage.Prod.AccountKey": "",
    "AzureStorage.Prod.AccountName": "",

    "DocumentIntelligence.Prod.ApiEndpoint": "",
    "DocumentIntelligence.Prod.ApiKey": "",
    "DocumentIntelligence.Prod.ClassifierModel": "",
    "DocumentIntelligence.Prod.ExtractorModel": "",
    "DocumentIntelligence.Prod.CheckExtractorModel": "",

    "FUNCTIONS_WORKER_RUNTIME": "java"
  },
  "Host": {
    "CORS": "*"
  }
}

```

## Test
To deploy the test environment, run:
```bash
gradle azureFunctionsDeploy
```

Be sure to update the function app environment variables with latest models 

## Prod
```bash
gradle azureFunctionsDeploy -Pprod=true
```

Be sure to update the function app environment variables with latest models


# Functions
### AnalyzePage
Use this function to reanalyze specific pages with the latest models.  
```zsh
curl -w "\n" http://localhost:7071/api/AnalyzePage --data "{\"pageRequests\":[{\"requestId\":\"commandlinerequest\",\"clientName\":\"test\",\"pdfPageData\":{\"fileName\":\"10_2024.pdf\",\"page\":9}}]}"
```

### GetDocumentDataModel
Retrieve the analyzed data model from azure storage using this function
```zsh
curl -w "\n" http://localhost:7071/api/GetDocumentDataModel --data "{\"requestId\":\"commandlinerequest\",\"clientName\":\"test\",\"pdfPageData\":{\"fileName\":\"10_2024.pdf\",\"page\":4}}"
```

### PutDocumentDataModel
This function will manually put a data model to a specific page, overriding the model that was analyzed using azure AI 
```zsh
curl -w "\n" http://localhost:7071/api/PutDocumentDataModel --data "{\"clientName\":\"test\",\"pdfPageData\":{\"fileName\":\"10_2024.pdf\",\"page\":5},\"model\":{\"statementDataModel\":{...}}"
```

### DeleteInputDocument
Will delete an input document including all the linked accompanying split pdfs, models, and statements
```zsh
curl -w "\n" http://localhost:7071/api/DeleteInputDocument --data "{\"clientName\":\"test\",\"filename\":\"test.pdf\"}"
```

### LoadTransactionsFromModel
Retrieve all the transactions from a specific filename/page (Note: that page must have already been analyzed)
```zsh
curl -w "\n" http://localhost:7071/api/LoadTransactionsFromModel --data "{\"requestId\": \"commandlinerequest\", \"clientName\": \"test\", \"pdfPageData\":{\"fileName\": \"NFCU_2022_SingleAccount.pdf\", \"page\": 2}, \"statementDate\": \"3/14/2022\"}"
```

### UpdateStatementModels
Not feasible to use without the accompanying UI.  With the provided data, this function will replace the saved models and create a new bank statement

# Split PDF Tool
Splits a PDF into multiple single pages.  This is useful for training the model.

Example usage:
```
gradle splitPdf -Pfilename="./testInput/BofA_7_2024.pdf" -Pargs='-p 1,3,4,5,6,7,8,9 -od ./testOutput -sep'
```
Instead of -p you can use:
* `-a` (all) 
* `-r 1,4` (for a range between page 1-4)

To debug, add `Pdebug=true`, then listen to localhost on port 5050

# Managed Agents
The entire managed-agent environment — skills, memory stores, sandbox environments, agents and deployments — is declared under `managed-agents/` and applied to the Anthropic API with one command, using the `com.anthropic:anthropic-java` SDK.

```bash
gradle applyAgents
```

### Directory layout
One top-level directory per resource type. Nothing is nested inside anything else, because nothing is nested server-side either — every resource is independent and refers to the others by id. The one exception is `shared/`, which isn't a resource type at all: it holds source material symlinked *into* the resource directories, and is never published on its own.

```
managed-agents/
  agents/
    bank-statement-extraction/
      agent.yaml            # `name` must equal the directory name
      system-prompt.md
      user-prompt.md        # runtime template — never published (see below)
  skills/
    bank-statement-extraction/
      SKILL.md              # frontmatter `name` must equal the directory name
      references/
        output-schema.md
        extraction-notes-format.md
  memory-stores/ 
  environments/
  deployments/
  shared/
```

`shared/` [Shared skill files](#shared-skill-files) for when to add shared files

besides agents and skills, each directory will deploy one resource per YAML file.  You may put any other file (such as a `.md` file) and reference it in the YAML (see [yaml-constructs](#yaml-constructs)) 
Resources are looked up by name, so re-applying updates the existing resource rather than creating a duplicate — there is no id lockfile to commit. A mismatch between a name and its directory or filename fails the command, since that would quietly create a second resource.

### Memory store layout
The two writable stores (`bank-patterns`, `extraction-notes`) hold **one folder per bank type**, not one file:

```
/mnt/memory/bank-patterns/bank_of_america/
  main.md                  # consolidated; only memory-consolidation writes it
  sesn_011CZxAbc123.md     # one per session, named for the session id
```

Session-driven agents run concurrently against the same store, so they never edit a shared file: each run reads `main.md` plus every session file, then writes at most one new file named for its own session id containing only what was new or different. The scheduled `memory-consolidation` deployments fold those session files back into `main.md` and delete the ones they consumed — without that pass the file count grows until it hits the store's 2,000-memory cap.


### Shared skill files
A published skill bundle has to be self-contained — the API has no cross-skill file sharing — so markdown that two skills share lives in `shared/skills/` and is symlinked into each skill's `references/`. **Nothing uses this today** — the mechanism is supported and tested, but `shared/` is currently absent; the file name below is illustrative:

```bash
ln -s ../../../shared/skills/some-shared-reference.md \
  managed-agents/skills/bank-statement-splitting/references/some-shared-reference.md
```

No code special-cases this: the loader's walk follows the link, so a shared file is hashed and uploaded like a real one, and editing it republishes every skill that links to it. Four things to know:

- **Link individual files, never directories.** The walk deliberately doesn't descend symlinked directories, so a directory link publishes nothing. Both that and a broken link fail the run rather than quietly shrinking the bundle.
- **Use a relative target**, so the link survives a fresh clone at any path.
- **A shared file arrives as an ordinary `references/` file**, which each `SKILL.md` has to point the agent at. So shared content must read as a standalone document — this mechanism suits whole sections and schema/format references, not a paragraph excised from mid-section.
- **macOS/Linux only.** A Windows checkout without `core.symlinks` materializes links as text files containing the path.

Files in `shared/` are grouped by what consumes them (`shared/skills/`), so a file's admission rule is "some bundle links to it" rather than "it seemed shared".

### Two kinds of agent
| | Session-driven | Deployment-driven |
|---|---|---|
| Examples | `bank-statement-extraction`, `bank-statement-splitting` | `memory-consolidation` |
| Trigger | a session created per PDF | cron schedule, or `deployments().run(id)` |
| Per-run input | yes, via `user-prompt.md` | none — deployments take no per-run input |
| Memory stores attached | at runtime, per session | declaratively, in the deployment's `resources` |
| Has a `deployments/` entry | no | yes |

### YAML constructs
Config files are the API's own JSON schema written as YAML, so they copy-paste to and from the Console. Two constructs fill in values that aren't known when the file is written. Both work in any config file, at any depth.

| Construct | Replaced with |
|---|---|
| `{file: system-prompt.md}` | the file's text, read relative to the containing YAML |
| `{resource: skill, name: bank-statement-extraction}` | that resource's id, resolved at publish time |

`{resource: …}` types are `skill`, `memory-store`, `environment`, `agent`. Because the id is supplied as a *value*, the surrounding field keeps its real API name:

```yaml
environment_id: {resource: environment, name: pdf-processing}
```

### Adding an agent
1. `mkdir managed-agents/agents/<name>` and write `agent.yaml` (with `name:` matching the directory) plus `system-prompt.md`.
2. Optionally add `managed-agents/skills/<name>/SKILL.md`, and reference it from `agent.yaml`.
3. If it should run on a schedule rather than per request, add a `managed-agents/deployments/<name>.yaml`.
4. `gradle applyAgents -Pagents=<name> -PdryRun=true`, then drop `-PdryRun`.

No code changes and no registration step — discovery is directory-driven.

### Applying
Everything is loaded and linted before the first network call, so a typo fails the run rather than leaving the workspace half-updated. Resources are then applied in dependency order: skills → memory stores → environments → agents → deployments.

Anything whose content hasn't changed is skipped, so re-running is a no-op and doesn't stack up identical versions.

```bash
# Preview: lints, resolves every reference, prints the plan, publishes nothing
gradle applyAgents -PdryRun=true

# Apply everything
gradle applyAgents

# Apply one agent and everything it references (its skill, deployment, stores, environment)
gradle applyAgents -Pagents="memory-consolidation"

# Apply only certain resource types (singular names: skill, memory-store, environment, agent, deployment)
gradle applyAgents -PresourceTypes="skill,agent"

# Skills only — equivalent to -PresourceTypes=skill
gradle updateSkill
```

### Lints
Run before anything is published:
- each `name` matches its directory or filename
- every `{file: …}` target exists and stays inside `managed-agents/`
- every `{resource: …}` resolves to something defined on disk
- every symlink inside a skill resolves to a regular file — a broken link, or a link to a directory, would otherwise drop a shared file from the bundle without a word
- every `/mnt/memory/<store>/` path mentioned in a skill or prompt matches a defined memory store — a store's name determines its mount path, so renaming one would otherwise silently detach it from the skill that reads it

### API key
The tasks inject the merged settings `Values` into the process (the same mechanism as `gradle run`), so `ANTHROPIC_API_KEY` is picked up without exporting it in your shell. Add it to the `Values` block of `common.local.settings.json` (pass `-Penv=<name>` to also merge `<name>.local.settings.json`):
```json
"Values": {
  "ANTHROPIC_API_KEY": "sk-ant-..."
}
```
If no settings file is present it falls back to the inherited shell environment — which is what a CI pipeline would use.

### Not handled yet
- **No test/prod split.** Resources are scoped to whichever workspace the API key belongs to.
- **No pruning.** Deleting a file or directory doesn't archive the remote resource.
- **No rollback.** A mid-run failure leaves earlier stages applied; every stage is idempotent, so the fix is to re-run.
- **Deployment pause state isn't managed.** If the API pauses a deployment, `applyAgents` won't notice or unpause it.

# Troubleshooting
### Unable to start AzureFunctionsRun: GRPC error on Mac
If you see the error
`java Grpc.Core: Error loading native library. Not found in any of the possible locations:...`

From googling, it appears to be a weird issue on mac. Just download the file from [github](https://github.com/einari/Grpc.Core.M1/blob/main/libgrpc_csharp_ext.arm64.dylib) and then symlink into the appropriate directory like
```bash
ln -s ~/Downloads/libgrpc_csharp_ext.arm64.dylib ~/.azure-functions-core-tools/Functions/ExtensionBundles/Microsoft.Azure.Functions.ExtensionBundle/4.21.0/bin/libgrpc_csharp_ext.arm64.dylib
```

### Slanted PDFs
Use [ocrmypdf](https://github.com/ocrmypdf/OCRmyPDF) `--deskew`.  By default it assumes you're deskewing because it is an image based PDF, but if there is a mixture of image/text, you might need to use `--force-ocr` as it won't override by default

```bash
ocrmypdf --deskew --force-ocr ~/Downloads/CC\ -\ Greenlight\ x1322\ 5.2025-9.2025.pdf "/Users/andrewgoldberg/Downloads/CC - Greenlight x1322 5.2025-9.2025[FIXED].pdf"
```

### Bad Quality PDFs
use [imagemagick](https://github.com/ImageMagick/ImageMagick) to convert the PDF to a high quality image then back to a higher quality PDF
```bash
magick -density 300 input.pdf output.pdf
```

