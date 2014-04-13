package org.deidentifier.arx.io.importdata;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.deidentifier.arx.io.datasource.Column;
import org.deidentifier.arx.io.datasource.JdbcConfiguration;

/**
 * Import adapter for JDBC
 *
 * This adapter can import data from JDBC sources. The source itself is
 * described by an appropriate {@link JdbcConfiguration} object.
 */
public class JdbcImportAdapter extends DataSourceImportAdapter {
    
    /**
     * The configuration describing the CSV file being used
     */
    private JdbcConfiguration config;

    /**
     * ResultSet to return
     *
     * @see {@link #next()}
     */
    private ResultSet resultSet;

    /**
     * Indicates whether there is another row to return
     */
    private boolean hasNext;

    /**
     * Indicates whether the first row has already been returned
     *
     * The first row contains the name of the columns. Depending upon whether
     * the name of the column has been assigned explicitly, this is either the
     * value of the table itself, the value defined by the user.
     */
    private boolean headerReturned;

    /**
     * Number of rows that need to be processed in total
     *
     * @see {@link #getProgress()}
     */
    private int totalRows;

    /**
     * Creates a new instance of this object with given configuration
     *
     * @param config {@link #config}
     *
     * @throws IOException In case of communication errors with JDBC
     */
    protected JdbcImportAdapter(JdbcConfiguration config) throws IOException
    {

        super(config);
        this.config = config;

        /* Preparation work */
        this.indexes = getIndexesToImport();
        this.dataTypes = getColumnDatatypes();

        try {

            Statement statement;

            /* Used to keep track of progress */
            statement = config.getConnection().createStatement();
            statement.execute("SELECT COUNT(*) FROM " + config.getTable());
            resultSet = statement.getResultSet();

            if (resultSet.next()) {

                totalRows = resultSet.getInt(1);

                if (totalRows == 0) {

                    throw new IOException("Table doesn't contain any rows");

                }

            } else {

                throw new IOException("Couldn't determine number of rows");

            }

            /* Query for actual data */
            statement = config.getConnection().createStatement();
            statement.execute("SELECT * FROM " + config.getTable());
            resultSet = statement.getResultSet();

            hasNext = resultSet.next();

        } catch (SQLException e) {

            throw new IOException(e.getMessage());

        }

    }

    /**
     * Returns an array with indexes of columns that should be imported
     *
     * This uses {@link DataSourceImportAdapter#getIndexesToImport()} and
     * increments each element by one, because JDBC starts counting at 1
     * when referring to columns.
     *
     * @return Array containing indexes of columns that should be imported
     *
     * @see {@link DataSourceImportAdapter#getIndexesToImport()}
     */
    @Override
    protected int[] getIndexesToImport()
    {

        int[] indexes = super.getIndexesToImport();

        for (int i = 0; i < indexes.length; i++) {

            indexes[i] += 1;

        }

        return indexes;

    }

    /**
     * Indicates whether there is another element to return
     *
     * This returns true when there is another element in the result set
     * {@link #resultSet}.
     */
    @Override
    public boolean hasNext()
    {

        return hasNext;

    }

    @Override
    public String[] next()
    {

        /* Return header in first iteration */
        if (!headerReturned) {

            return createHeader();

        }

        try {

            /* Create regular row */
            String[] result = new String[indexes.length];

            for (int i = 0; i < indexes.length; i++) {

                result[i] = resultSet.getString(indexes[i]);

                if (!dataTypes[i].isValid(result[i])) {

                    throw new IllegalArgumentException("Data value does not match data type");

                }

            }

            /* Move cursor forward and assign result to {@link #hasNext} */
            hasNext = resultSet.next();

            return result;

        } catch (SQLException e) {

            throw new RuntimeException("Couldn't retrieve data from database");

        }


    }

    /**
     * Creates the header row
     *
     * This returns a string array with the names of the columns that will be
     * returned later on by iterating over this object. Depending upon whether
     * or not names have been assigned explicitly either the appropriate values
     * will be returned, or names from the JDBC metadata will be used.
     */
    private String[] createHeader()
    {

        /* Initialization */
        String[] header = new String[config.getColumns().size()];
        List<Column> columns = config.getColumns();

        /* Create header */
        for (int i = 0, len = columns.size(); i < len; i++) {

            Column column = columns.get(i);

            /* Check whether name has been assigned explicitly or is nonempty */
            if (column.getAliasName() != null && !column.getAliasName().equals("")) {

                header[i] = column.getAliasName();

            } else {

                /* Assign name from JDBC metadata */
                try {

                    /* +1 offset, because counting in JDBC starts at 1 */
                    header[i] = resultSet.getMetaData().getColumnName(column.getIndex() + 1);

                } catch (SQLException e) {

                    throw new RuntimeException("Couldn't retrieve column name from metadata");

                }

            }

            column.setAliasName(header[i]);

        }

        headerReturned = true;

        /* Return header */
        return header;

    }

    /**
     * Dummy
     */
    @Override
    public void remove()
    {

        throw new UnsupportedOperationException();

    }

    /**
     * Returns the percentage of data that has already been returned
     *
     * This divides the number of rows that have already been returned by the
     * number of total rows and casts the result into a percentage. In case of
     * an {@link SQLException} 0 will be returned.
     */
    @Override
    public int getProgress()
    {

        try {

            return (int)((double)resultSet.getRow() / (double)totalRows * 100d);

        } catch (SQLException e) {

            return 0;

        }

    }

}
