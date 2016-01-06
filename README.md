# SVToMySQL

* Used to read in a *separated-value (csv, tsv, etc) file into a MySQL table.
* The service expects the table you want to insert to to have already been created.
* The service expects the data you're trying to insert to be valid. If not, a batch may fail.
* The service is NOT idempotent, empty the table before trying to re-insert.
* You can choose batch-size to increase performance (default is 200k).
* You can specify character-encoding on the input file.

# Initialization
 ```
     /**
     * @param host DB Host
     * @param port Port
     * @param database Database Name
     * @param user User Name with appropriate privileges
     * @param password User password (leave null to omit password)
     */
    public SVToMySQLService(String host, String port, String database, String user, String password)
  ```
 
 Basic usage:
 ```
  /**
  * @param tableName Table to insert into.
  * @param filePath Path to the file.
  * @param fileEncoding File encoding (ie, UTF-8)
  * @param delimiter What you're separating on (ie, "," - comma, "\\t" - tab)
  * @param ignoreFirstRow Is the first row of the file a column description, and we shouldn't insert it?
  * @param enforceDoubleQuotes Remove existing quotes from any parsed row value, and then add quotes to ALL row values (safer for MySQL insertion)
  * @param batchSize How many rows should be read/inserted at one time.
  */
    public void saveSVFileToTable(String tableName, String filePath, String fileEncoding, String delimiter, boolean ignoreFirstRow, boolean enforceDoubleQuotes,
            int batchSize) {
```

### Note that there are a few prepared methods to make life easier:
```
  public void saveTSVFileWithHeaderToTable(String tableName, String filePath)
  public void saveTSVFileWithoutHeaderToTable(String tableName, String filePath)
  public void saveCSVFileWithHeaderToTable(String tableName, String filePath)
  public void saveCSVFileWithoutHeaderToTable(String tableName, String filePath)
```
# Example

```
  SVToMySQLService tsvService = new SVToMySQLService("localhost", "3306", "awesome_db", "root");
  tsvService.saveTSVFileWithHeaderToTable("awesome_table", "C:\\lazy_file.tsv");
```
