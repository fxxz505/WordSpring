# ECDICT Free Vocabulary Import

WordSpring supports offline UTF-8 CSV import. For an openly licensed English-Chinese
source, use [ECDICT](https://github.com/skywind3000/ECDICT), whose repository is MIT licensed.
Obtain the upstream `stardict.csv` yourself, then convert only the list needed:

```powershell
python .\tools\convert_ecdict.py <path-to-stardict.csv> .\cet4.csv --tag cet4
python .\tools\convert_ecdict.py <path-to-stardict.csv> .\cet6.csv --tag cet6
python .\tools\convert_ecdict.py <path-to-stardict.csv> .\gaokao.csv --tag gk
```

Import the resulting CSV from a word book's **Import CSV** action. Keep the generated
`LICENSE-ECDICT.txt` with every redistributed converted list. ECDICT's README notes that
its entries are assembled from several resources, so review upstream provenance before
shipping a large derivative word-list in a public release.

## Built-in complete vocabularies

The packaged WordSpring collections are all version `2.0.0` and contain 4,500
case-insensitively unique English entries each. They are generated from ECDICT and retain
the upstream MIT license in each book folder's `LICENSE.txt`.

- CET-6 uses ECDICT's `cet6` tagged vocabulary.
- CET-4 and Gaokao start from their respective `cet4` and `gk` tags, then add related
  ECDICT tagged entries to reach 4,000 items.
- WordSpring Complete 4500 prioritizes CET-4 entries, then uses the same related tags.

These are expanded study collections rather than an assertion of an official exam syllabus.

