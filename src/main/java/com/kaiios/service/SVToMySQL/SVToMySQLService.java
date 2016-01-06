package com.kaiios.service.SVToMySQL;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * Reads .*sv files and inserts the values into a MySQL table.
 * 
 * Uses batching for larger files.
 * 
 * Expects table to already be prepared in the database.
 * 
 * Expects data to be valid for created table.
 * 
 * @author Kyle Kury
 *
 */
public class SVToMySQLService {
    private String connectionString;

    public SVToMySQLService(String host, String port, String database, String user) {
        this(host, port, database, user, null);
    }

    /**
     * @param host DB Host
     * @param port Port
     * @param database Database Name
     * @param user User Name with appropriate privileges
     * @param password User password (leave null to omit password)
     */
    public SVToMySQLService(String host, String port, String database, String user, String password) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        StringBuffer connectionStringBuffer = new StringBuffer();
        connectionStringBuffer.append("jdbc:mysql://").append(host).append(":").append(port);
        connectionStringBuffer.append("/").append(database).append("?user=").append(user);

        if (password != null) {
            connectionStringBuffer.append("&password=").append(password);
        }

        this.connectionString = connectionStringBuffer.toString();
    }

    
    /**
     * Add each row in the UTF-8 tab-separated file into the given table while
     * ignoring the first row, enforcing double-quotes, and using a batch-size of 200k.
     * 
     * @param tableName Table to insert into.
     * @param filePath Path to the file.
     */
    public void saveTSVFileWithHeaderToTable(String tableName, String filePath) {
        saveSVFileToTable(tableName, filePath, "UTF-8", "\\t", true, true, 200000);
    }
    
    /**
     * Add each row in the UTF-8 tab-separated file into the given table while
     * enforcing double-quotes, using a batch-size of 200k.
     * 
     * @param tableName Table to insert into.
     * @param filePath Path to the file.
     */
    public void saveTSVFileWithoutHeaderToTable(String tableName, String filePath) {
        saveSVFileToTable(tableName, filePath, "UTF-8", "\\t", false, true, 200000);
    }
    
    /**
     * Add each row in the UTF-8 comma-separated file into the given table while
     * ignoring the first row, enforcing double-quotes, and using a batch-size of 200k. 
     * 
     * @param tableName Table to insert into.
     * @param filePath Path to the file.
     */
    public void saveCSVFileWithHeaderToTable(String tableName, String filePath) {
        saveSVFileToTable(tableName, filePath, "UTF-8", ",", true, true, 200000);
    }
    
    /**
     * Add each row in the UTF-8 comma-separated file into the given table while
     * enforcing double-quotes, and using a batch-size of 200k. 
     * 
     * @param tableName Table to insert into.
     * @param filePath Path to the file.
     */
    public void saveCSVFileWithoutHeaderToTable(String tableName, String filePath) {
        saveSVFileToTable(tableName, filePath, "UTF-8", ",", false, true, 200000);
    }
    
    /**
     * Add each row in the *SV file into the given table.
     * 
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
        System.out.println("Processing " + filePath);
        Connection connection = null;

        try {
            connection = DriverManager.getConnection(connectionString);

            BufferedReader fileReader = null;

            try {
                fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(new File(filePath)), fileEncoding));

                String line;
                int currentBatch = 0;
                List<String[]> batchedRows = new ArrayList<String[]>();
                while ((line = fileReader.readLine()) != null) {
                    if (ignoreFirstRow) {
                        ignoreFirstRow = false;
                        continue;
                    }

                    // Remove all double-quotes
                    if (enforceDoubleQuotes) {
                        line = line.replaceAll("\"", "");
                    }

                    String[] rowValues = line.split(delimiter);

                    if (enforceDoubleQuotes) {
                        for (int j = 0; j < rowValues.length; j++) {
                            rowValues[j] = "\"" + rowValues[j] + "\"";
                        }
                    }

                    batchedRows.add(rowValues);

                    if (batchedRows.size() >= batchSize) {
                        currentBatch += batchedRows.size();
                        System.out.println("Processing batch " + currentBatch);
                        insertMySQLRows(connection, tableName, batchedRows);
                        batchedRows.clear();
                    }
                }

                if (!batchedRows.isEmpty()) {
                    System.out.println("Processing remaining " + batchedRows.size());
                    insertMySQLRows(connection, tableName, batchedRows);
                    currentBatch += batchedRows.size();
                }

                System.out.println("Processed " + currentBatch + " rows.");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    fileReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Assumes all columns are present.
     * Assumes table is present.
     * Assumes data is valid and correctly escaped (use enforceDoubleQuotes for safety).
     * 
     * @param connection MySQL connection
     * @param tableName Table to insert into
     * @param rows List of value-sets to insert
     */
    private void insertMySQLRows(Connection connection, String tableName, List<String[]> rows) {
        try {
            StringBuffer queryBuilder = new StringBuffer();
            queryBuilder.append("INSERT INTO ");
            queryBuilder.append("`" + tableName + "`");
            queryBuilder.append(" VALUES ");

            for (int j = 0; j < rows.size(); j++) {
                queryBuilder.append("(");
                for (int i = 0; i < rows.get(j).length; i++) {
                    queryBuilder.append(rows.get(j)[i]);

                    if (i < rows.get(j).length - 1) {
                        queryBuilder.append(",");
                    }
                }

                queryBuilder.append(")");
                if (j < rows.size() - 1) {
                    queryBuilder.append(",");
                }
            }

            queryBuilder.append(";");
            connection.createStatement().execute(queryBuilder.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
