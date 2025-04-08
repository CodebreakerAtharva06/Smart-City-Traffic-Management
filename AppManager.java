package example;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.functions;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.sql.Column;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AppManager {

    private static SparkSession sparkSession;
    private static Dataset<Row> dataFrame;

    // Singleton pattern for SparkSession
    public static SparkSession getSparkSession() {
        if (sparkSession == null) {
            sparkSession = SparkSession.builder()
                    .appName("Traffic Prediction")
                    .master("local[*]") // Use all available CPU cores
                    .config("spark.sql.legacy.timeParserPolicy", "LEGACY")
                    .getOrCreate();
        }
        return sparkSession;
    }
    public static Dataset<Row> getDataFrame(String filePath) {
        if (dataFrame == null) {
            dataFrame = loadDataset(filePath);
        }
        return dataFrame;
    }

    // Load and process the dataset
    public static Dataset<Row> loadDataset(String filePath) {
        SparkSession spark = getSparkSession();

        // Debug: Print file path
        System.out.println("Loading dataset from: " + filePath);

        // Check if file exists
        File dataFile = new File(filePath);
        if (!dataFile.exists()) {
            System.err.println("Error: Dataset file not found at " + dataFile.getAbsolutePath());
            return null;
        }

        try {
            // Load CSV with schema inference
            Dataset<Row> df = spark.read()
                    .option("header", "true")
                    .option("inferSchema", "true")
                    .csv(filePath);

            // Debug: Print raw schema and sample data
            System.out.println("Raw schema:");
            df.printSchema();
            System.out.println("Sample data (raw):");
            df.show(5);

            // Print all column names to help with debugging
            System.out.println("All column names: " + String.join(", ", df.columns()));

            // Handle Time column and extract Hour
            if (Arrays.asList(df.columns()).contains("Time")) {
                df = df.withColumnRenamed("Time", "RawTime")
                        .withColumn("Time", functions.col("RawTime").cast("string"))
                        .withColumn("Hour", functions.substring(functions.col("RawTime"), 1, 2).cast("int"));
            } else if (!Arrays.asList(df.columns()).contains("Hour")) {
                df = df.withColumn("Hour", functions.lit(12)); // Default to noon if no time available
            }

            // Cast 'Vehicles' column to integer if it exists
            if (Arrays.asList(df.columns()).contains("Vehicles")) {
                df = df.na().fill(0, new String[]{"Vehicles"})
                        .withColumn("Vehicles", functions.col("Vehicles").cast("int"));
            } else {
                df = df.withColumn("Vehicles", functions.lit(0));
            }

            // Handle Accidents Reported column if it exists
            if (Arrays.asList(df.columns()).contains("Accidents Reported")) {
                df = df.na().fill(0, new String[]{"Accidents Reported"})
                        .withColumn("Accidents Reported", functions.col("Accidents Reported").cast("int"))
                        .withColumnRenamed("Accidents Reported", "Accidents_Reported");
            } else if (!Arrays.asList(df.columns()).contains("Accidents_Reported")) {
                df = df.withColumn("Accidents_Reported", functions.lit(0));
            }

            // Rename Road Condition column for consistency if it exists
            if (Arrays.asList(df.columns()).contains("Road Condition")) {
                df = df.withColumnRenamed("Road Condition", "RoadCondition");
            } else if (Arrays.asList(df.columns()).contains("Road_Condition")) {
                df = df.withColumnRenamed("Road_Condition", "RoadCondition");
            } else if (!Arrays.asList(df.columns()).contains("RoadCondition")) {
                df = df.withColumn("RoadCondition", functions.lit("Normal"));
            }

            // Handle DayOfWeek column
            if (!Arrays.asList(df.columns()).contains("DayOfWeek") && Arrays.asList(df.columns()).contains("Date")) {
                df = df.withColumn("DayOfWeek",
                        functions.date_format(functions.to_date(functions.col("Date"), "yyyy-MM-dd"), "u").cast("int"));
            }

            // Debugging: Check if DayOfWeek column was created
            if (!Arrays.asList(df.columns()).contains("DayOfWeek")) {
                System.err.println("Error: DayOfWeek column was not created!");
                df = df.withColumn("DayOfWeek", functions.lit(0)); // Add default column if missing
            }

            // Drop RawTime column if it exists
            if (Arrays.asList(df.columns()).contains("RawTime")) {
                df = df.drop("RawTime");
            }

            // Build column list based on what exists in the DataFrame
            List<String> finalColumns = new ArrayList<>();

            if (!Arrays.asList(df.columns()).contains("Date")) {
                df = df.withColumn("Date", functions.current_date());
            }
            finalColumns.add("Date");

            if (!Arrays.asList(df.columns()).contains("Junction")) {
                df = df.withColumn("Junction", functions.lit("Default Junction"));
            }
            finalColumns.add("Junction");

            finalColumns.add("Time");
            finalColumns.add("Hour");
            finalColumns.add("Vehicles");
            finalColumns.add("Accidents_Reported");
            finalColumns.add("RoadCondition");

            if (Arrays.asList(df.columns()).contains("Weather")) {
                finalColumns.add("Weather");
            }

            if (Arrays.asList(df.columns()).contains("DayOfWeek")) {
                finalColumns.add("DayOfWeek");
            }

            // Debug: Print finalColumns list
            System.out.println("Final columns to select: " + finalColumns);

            // Select the final columns
            df = df.select(finalColumns.stream().map(functions::col).toArray(Column[]::new));

            // Debug: Print final schema and sample data
            System.out.println("Final schema:");
            df.printSchema();
            System.out.println("Sample data (final):");
            df.show(5);

            // Persist the DataFrame in memory
            df.persist(StorageLevel.MEMORY_AND_DISK());

            System.out.println("Dataset loaded successfully with " + df.count() + " records.");
            return df;

        } catch (Exception e) {
            System.err.println("Error processing dataset: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        String filePath = "D:/3rd year/6th sem/BDT/Mini Project/final_cleaned_pune_traffic.csv";

        if (args.length > 0) {
            filePath = args[0];
        }

        Dataset<Row> df = loadDataset(filePath);

        if (df != null) {
            System.out.println("Sample data:");
            df.show(5);

            System.out.println("Records by junction:");
            df.groupBy("Junction").count().orderBy(functions.desc("count")).show();

            if (Arrays.asList(df.columns()).contains("Weather")) {
                System.out.println("Records by weather condition:");
                df.groupBy("Weather").count().orderBy(functions.desc("count")).show();
            }

            if (Arrays.asList(df.columns()).contains("RoadCondition")) {
                System.out.println("Average metrics by road condition:");
                df.groupBy("RoadCondition")
                        .agg(
                                functions.avg("Vehicles").alias("avg_vehicles"),
                                functions.avg("Accidents_Reported").alias("avg_accidents")
                        )
                        .orderBy("RoadCondition")
                        .show();
            }
        } else {
            System.err.println("Failed to load dataset. Exiting program.");
            System.exit(1);
        }
    }
}
