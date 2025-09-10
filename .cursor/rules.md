### Data classes
All data classes must use proper jackson annotations @JsonProperty and @Inject like 

```
data class PutDocumentDataModelResponse @Inject constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("model") val model: DocumentDataModel
)
```

### APIs
All API function classes must be injected in the AppModule like

```
@Provides
@Singleton
fun loadTransactionsFromModelFunction(
    azureStorageDataManager: AzureStorageDataManager
) = LoadTransactionsFromModelFunction(azureStorageDataManager)
```

### Coding Rules
* Import classes, don't spell out the full package name, unless it's necessary due to a conflict.

### Guidelines
* include helper functions in the class itself instead of outside the class.  In general, files should have a class in it and that's it
* companion objects should be the last thing declared in a class