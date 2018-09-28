# report-classifier

Use IBM Watson to create a situation report classifier.

Created for the IBM Call for Code Challenge.

## APIs used

https://reliefweb.int/help/api


## Other Useful References

https://console.bluemix.net/docs/services/natural-language-classifier/getting-started.html#natural-language-classifier

## Usage

1. Collect Data

```lein run -m report-classifier.collect```

will run for a while and generate CSV files classifier_[12].csv in the local directory.

2. Create Classifiers

Use functions in report-classifier.watson to call the NLC API and create classifiers using the generated CSVs.

3. Call Classifiers

```lein run config.edn Add your text to classify here```

Classifier will report clusters that made to the identified themes.

## License

Copyright © 2018 Andrew Whitehouse

Distributed under the Apache 2.0 License https://www.apache.org/licenses/LICENSE-2.0
