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

# Troubleshooting
### Unable to start AzureFunctionsRun: GRPC error on Mac
If you see the error
`java Grpc.Core: Error loading native library. Not found in any of the possible locations:...`

From googling, it appears to be a weird issue on mac. Just download the file from [github](https://github.com/einari/Grpc.Core.M1/blob/main/libgrpc_csharp_ext.arm64.dylib) and then symlink into the appropriate directory like
```bash
ln -s ~/Downloads/libgrpc_csharp_ext.arm64.dylib ~/.azure-functions-core-tools/Functions/ExtensionBundles/Microsoft.Azure.Functions.ExtensionBundle/4.21.0/bin/libgrpc_csharp_ext.arm64.dylib
```