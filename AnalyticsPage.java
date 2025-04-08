package example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.*;

public class AnalyticsPage {

    private static final String DATASET_PATH = "D:/3rd year/6th sem/BDT/Mini Project/final_cleaned_pune_traffic.csv";
    private static SparkSession spark;
    private static Dataset<Row> trafficData;

    private static ComboBox<String> junctionDropdown;
    private static DatePicker startDatePicker;
    private static DatePicker endDatePicker;
    private static ComboBox<String> timeRangeDropdown;
    private static Label peakCongestionLabel;
    private static TextArea congestionReportArea;
    private static TableView<DataEntry> analysisTable;

    // Initialize Spark Session (Single instance)
    static {
        initializeSparkSession();
        loadDataset();
    }

    // Show Analytics Page
    public static void show(Stage primaryStage) {
        // Set up junction selection
        Label junctionLabel = new Label("Select Junction:");
        junctionDropdown = createJunctionDropdown();
        
        // Set up date range selection
        Label dateRangeLabel = new Label("Select Date Range:");
        startDatePicker = new DatePicker(LocalDate.now().minusDays(30));
        endDatePicker = new DatePicker(LocalDate.now());
        startDatePicker.setPromptText("Start Date");
        endDatePicker.setPromptText("End Date");
        
        // Set up time range selection
        Label timeRangeLabel = new Label("Time Period:");
        timeRangeDropdown = new ComboBox<>();
        timeRangeDropdown.getItems().addAll(
                "All Day",
                "Morning (6:00-12:00)",
                "Afternoon (12:00-16:00)",
                "Evening (16:00-20:00)",
                "Night (20:00-6:00)"
        );
        timeRangeDropdown.setValue("All Day");
        
        // Update button
        Button updateButton = new Button("Generate Analysis");
        updateButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8px 16px;");
        
        // Set up filter controls in grid
        GridPane filterControls = new GridPane();
        filterControls.setHgap(10);
        filterControls.setVgap(10);
        filterControls.setPadding(new Insets(10));
        
        filterControls.add(junctionLabel, 0, 0);
        filterControls.add(junctionDropdown, 1, 0);
        filterControls.add(dateRangeLabel, 0, 1);
        
        HBox dateBox = new HBox(10, startDatePicker, new Label("to"), endDatePicker);
        filterControls.add(dateBox, 1, 1);
        
        filterControls.add(timeRangeLabel, 0, 2);
        filterControls.add(timeRangeDropdown, 1, 2);
        filterControls.add(updateButton, 1, 3);
        
        // Create chart and configure it
        LineChart<String, Number> lineChart = createLineChart();
        
        // Create congestion report area
        congestionReportArea = new TextArea();
        congestionReportArea.setEditable(false);
        congestionReportArea.setPrefHeight(150);
        congestionReportArea.setPromptText("Congestion report will appear here...");
        congestionReportArea.setStyle("-fx-font-family: 'Consolas', monospace;");
        
        peakCongestionLabel = new Label();
        peakCongestionLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #e74c3c;");
        
        // Create analysis table
        analysisTable = createAnalysisTable();
        
        // Set up back button
        Button backButton = new Button("Back to Home");
        backButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8px 16px;");
        backButton.setOnAction(e -> HomePage.show(primaryStage));
        
        // Handle update button action
        updateButton.setOnAction(e -> {
            String selectedJunction = junctionDropdown.getValue();
            LocalDate startDate = startDatePicker.getValue();
            LocalDate endDate = endDatePicker.getValue();
            String timeRange = timeRangeDropdown.getValue();
            
            if (selectedJunction != null && startDate != null && endDate != null) {
                updateLineChart(lineChart, selectedJunction, startDate, endDate, timeRange);
                updateAnalysisTable(selectedJunction, startDate, endDate, timeRange);
                updateCongestionReport(selectedJunction, startDate, endDate, timeRange);
            } else {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Missing Information");
                alert.setHeaderText(null);
                alert.setContentText("Please select all required filters (Junction, Start Date, End Date)");
                alert.showAndWait();
            }
        });
        
        // Set up main layout
        VBox layout = new VBox(10);
        layout.setPadding(new Insets(20));
        
        // Add components to layout - Fixed Label creation
        Label titleLabel = new Label("Traffic Analytics Dashboard");
        titleLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        
        Label trendLabel = new Label("Traffic Volume Trend");
        trendLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label reportLabel = new Label("Congestion Report");
        reportLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        Label analysisLabel = new Label("Detailed Analysis");
        analysisLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        
        layout.getChildren().addAll(
                titleLabel,
                filterControls,
                trendLabel,
                lineChart,
                reportLabel,
                congestionReportArea,
                peakCongestionLabel,
                analysisLabel,
                analysisTable,
                backButton
        );
        
        // Set up scene
        Scene scene = new Scene(layout, 900, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Traffic Analytics");
        
        primaryStage.setOnCloseRequest(event -> {
            if (spark != null) {
                spark.stop();
            }
        });
        
        primaryStage.show();
        
        // Initial data load for the default junction
        if (junctionDropdown.getValue() != null) {
            updateButton.fire();
        }
    }

    // Initialize Spark Session
    private static void initializeSparkSession() {
        if (spark == null) {
            spark = SparkSession.builder()
                    .appName("Traffic Analytics")
                    .master("local[*]")
                    .getOrCreate();
        }
    }

    // Load Dataset with Explicit Schema
    private static void loadDataset() {
        StructType schema = new StructType()
                .add("Date", DataTypes.StringType)
                .add("Time", DataTypes.StringType)
                .add("Junction", DataTypes.StringType)
                .add("Vehicles", DataTypes.IntegerType)
                .add("Accidents Reported", DataTypes.IntegerType)
                .add("Weather", DataTypes.StringType)
                .add("Road Condition", DataTypes.StringType);

        trafficData = spark.read()
                .option("header", "true")
                .schema(schema)
                .csv(DATASET_PATH)
                .withColumn("Date", to_date(col("Date"), "yyyy-MM-dd"))
                .withColumn("Hour", expr("cast(split(Time, ':')[0] as int)"))
                .filter(col("Junction").isNotNull());

        // Schema Debugging
        trafficData.printSchema();
        System.out.println("Dataset loaded with " + trafficData.count() + " records");
    }

    // Create Junction Dropdown
    private static ComboBox<String> createJunctionDropdown() {
        ComboBox<String> comboBox = new ComboBox<>();
        populateJunctions(comboBox);
        comboBox.setPromptText("Select Junction");
        comboBox.setStyle("-fx-font-size: 14px;");
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    // Populate Junction Dropdown
    private static void populateJunctions(ComboBox<String> comboBox) {
        if (trafficData == null) return;

        try {
            // Get distinct junction names
            Dataset<Row> distinctJunctions = trafficData.select("Junction").distinct().orderBy("Junction");
            
            // Convert to Java List
            List<String> junctionList = distinctJunctions.collectAsList().stream()
                    .map(row -> row.getString(0))
                    .filter(junction -> junction != null && !junction.isEmpty())
                    .collect(Collectors.toList());

            // Update UI on JavaFX thread
            Platform.runLater(() -> {
                comboBox.getItems().clear();
                comboBox.getItems().addAll(junctionList);
                
                // Select first junction if available
                if (!junctionList.isEmpty()) {
                    comboBox.setValue(junctionList.get(0));
                }
            });

            System.out.println("Found " + junctionList.size() + " unique junctions");
            
        } catch (Exception e) {
            System.err.println("Error populating junctions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Create Line Chart
    private static LineChart<String, Number> createLineChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Date");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Number of Vehicles");

        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Traffic Trends Over Time");
        lineChart.setPrefHeight(350);
        
        return lineChart;
    }

    // Update Line Chart with date range and time filtering
    private static void updateLineChart(LineChart<String, Number> lineChart, String junction, 
                               LocalDate startDate, LocalDate endDate, String timeRange) {
        lineChart.getData().clear();

        if (junction == null) return;

        try {
            // Apply time range filter
            Dataset<Row> filteredByTime = applyTimeRangeFilter(trafficData, timeRange);
            
            // Filter by junction and date range
            Dataset<Row> filteredData = filteredByTime
                    .filter(col("Junction").equalTo(junction))
                    .filter(col("Date").geq(lit(java.sql.Date.valueOf(startDate))))
                    .filter(col("Date").leq(lit(java.sql.Date.valueOf(endDate))))
                    .groupBy("Date")
                    .agg(sum("Vehicles").alias("Total_Vehicles"))
                    .orderBy("Date");

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(junction + " Traffic Flow");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd");
            List<Row> rows = filteredData.collectAsList();
            
            for (Row row : rows) {
                LocalDate date = row.getDate(0).toLocalDate();
                String formattedDate = date.format(formatter);
                Number vehicleCount = row.getLong(1);
                series.getData().add(new XYChart.Data<>(formattedDate, vehicleCount));
            }

            lineChart.getData().add(series);
            
            System.out.println("Updated chart with " + rows.size() + " data points for junction: " + junction);
            
        } catch (Exception e) {
            System.err.println("Error updating chart: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method to apply time range filter
    private static Dataset<Row> applyTimeRangeFilter(Dataset<Row> data, String timeRange) {
        switch (timeRange) {
            case "Morning (6:00-12:00)":
                return data.filter(col("Hour").geq(6).and(col("Hour").lt(12)));
            case "Afternoon (12:00-16:00)":
                return data.filter(col("Hour").geq(12).and(col("Hour").lt(16)));
            case "Evening (16:00-20:00)":
                return data.filter(col("Hour").geq(16).and(col("Hour").lt(20)));
            case "Night (20:00-6:00)":
                return data.filter(col("Hour").geq(20).or(col("Hour").lt(6)));
            default:
                return data; // All Day
        }
    }

    // Create Analysis Table
    private static TableView<DataEntry> createAnalysisTable() {
        TableView<DataEntry> table = new TableView<>();

        TableColumn<DataEntry, LocalDate> dateColumn = new TableColumn<>("Date");
        dateColumn.setCellValueFactory(data -> data.getValue().dateProperty());
        dateColumn.setPrefWidth(120);

        TableColumn<DataEntry, String> timeColumn = new TableColumn<>("Time");
        timeColumn.setCellValueFactory(data -> data.getValue().timeProperty());
        timeColumn.setPrefWidth(100);

        TableColumn<DataEntry, Integer> countColumn = new TableColumn<>("Vehicle Count");
        countColumn.setCellValueFactory(data -> data.getValue().vehicleCountProperty().asObject());
        countColumn.setPrefWidth(120);
        
        TableColumn<DataEntry, String> weatherColumn = new TableColumn<>("Weather");
        weatherColumn.setCellValueFactory(data -> data.getValue().weatherProperty());
        weatherColumn.setPrefWidth(120);
        
        TableColumn<DataEntry, String> roadConditionColumn = new TableColumn<>("Road Condition");
        roadConditionColumn.setCellValueFactory(data -> data.getValue().roadConditionProperty());
        roadConditionColumn.setPrefWidth(150);

        table.getColumns().addAll(dateColumn, timeColumn, countColumn, weatherColumn, roadConditionColumn);
        table.setPrefHeight(200);
        VBox.setVgrow(table, Priority.ALWAYS);

        return table;
    }

    // Update Analysis Table
    private static void updateAnalysisTable(String junction, LocalDate startDate, LocalDate endDate, String timeRange) {
        if (junction == null || junction.isEmpty()) return;

        try {
            // Apply time range filter
            Dataset<Row> filteredByTime = applyTimeRangeFilter(trafficData, timeRange);
            
            // Filter by junction and date range
            Dataset<Row> filteredData = filteredByTime
                    .filter(col("Junction").equalTo(junction))
                    .filter(col("Date").geq(lit(java.sql.Date.valueOf(startDate))))
                    .filter(col("Date").leq(lit(java.sql.Date.valueOf(endDate))))
                    .orderBy("Date", "Time");

            List<Row> rowList = filteredData.collectAsList();
            ObservableList<DataEntry> data = FXCollections.observableArrayList();

            for (Row row : rowList) {
                LocalDate date = row.getDate(0).toLocalDate();
                String timeStr = row.getString(1);
                int vehicleCount = row.getInt(3);
                String weather = row.getString(5);
                String roadCondition = row.getString(6);

                data.add(new DataEntry(date, timeStr, vehicleCount, weather, roadCondition));
            }

            Platform.runLater(() -> {
                analysisTable.setItems(data);
            });

            System.out.println("Updated table with " + data.size() + " rows for junction: " + junction);

        } catch (Exception e) {
            System.err.println("Error updating table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Update Congestion Report
    private static void updateCongestionReport(String junction, LocalDate startDate, LocalDate endDate, String timeRange) {
        if (junction == null || junction.isEmpty()) return;

        try {
            // Apply time range filter
            Dataset<Row> filteredByTime = applyTimeRangeFilter(trafficData, timeRange);
            
            // Filter by junction and date range
            Dataset<Row> filteredData = filteredByTime
                    .filter(col("Junction").equalTo(junction))
                    .filter(col("Date").geq(lit(java.sql.Date.valueOf(startDate))))
                    .filter(col("Date").leq(lit(java.sql.Date.valueOf(endDate))));
            
            // Generate congestion report
            Dataset<Row> stats = filteredData.agg(
                    count("Vehicles").alias("Total_Records"),
                    sum("Vehicles").alias("Total_Vehicles"),
                    avg("Vehicles").alias("Avg_Vehicles"),
                    max("Vehicles").alias("Max_Vehicles"),
                    min("Vehicles").alias("Min_Vehicles"),
                    sum("Accidents Reported").alias("Total_Accidents")
            );
            
            // Find peak congestion time
            Dataset<Row> peakCongestion = filteredData
                    .orderBy(desc("Vehicles"))
                    .limit(1);
            
            // Build report text
            StringBuilder report = new StringBuilder();
            
            List<Row> statsList = stats.collectAsList();
            if (!statsList.isEmpty()) {
                Row statsRow = statsList.get(0);
                report.append("TRAFFIC CONGESTION REPORT\n");
                report.append("==========================\n");
                report.append("Junction: ").append(junction).append("\n");
                report.append("Period: ").append(startDate).append(" to ").append(endDate).append("\n");
                report.append("Time Range: ").append(timeRange).append("\n\n");
                report.append("Total Records: ").append(statsRow.getLong(0)).append("\n");
                report.append("Total Vehicles: ").append(statsRow.getLong(1)).append("\n");
                report.append("Average Vehicles: ").append(String.format("%.2f", statsRow.getDouble(2))).append("\n");
                report.append("Maximum Vehicles: ").append(statsRow.getLong(3)).append("\n");
                report.append("Minimum Vehicles: ").append(statsRow.getLong(4)).append("\n");
                report.append("Total Accidents: ").append(statsRow.getLong(5)).append("\n");
            }
            
            // Update UI
            Platform.runLater(() -> {
                congestionReportArea.setText(report.toString());
            });
            
            // Update peak congestion label
            List<Row> peakList = peakCongestion.collectAsList();
            if (!peakList.isEmpty()) {
                Row peakRow = peakList.get(0);
                LocalDate peakDate = peakRow.getDate(0).toLocalDate();
                String peakTime = peakRow.getString(1);
                int peakVehicles = peakRow.getInt(3);
                
                Platform.runLater(() -> {
                    peakCongestionLabel.setText("🚨 Peak Congestion: " + peakDate + 
                            " at " + peakTime + " with " + peakVehicles + " vehicles");
                });
            }
            
            System.out.println("Generated congestion report for junction: " + junction);
            
        } catch (Exception e) {
            System.err.println("Error generating congestion report: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // DataEntry class for table view
    public static class DataEntry {
        private final javafx.beans.property.ObjectProperty<LocalDate> date;
        private final javafx.beans.property.StringProperty time;
        private final javafx.beans.property.IntegerProperty vehicleCount;
        private final javafx.beans.property.StringProperty weather;
        private final javafx.beans.property.StringProperty roadCondition;
        
        public DataEntry(LocalDate date, String time, int vehicleCount, String weather, String roadCondition) {
            this.date = new javafx.beans.property.SimpleObjectProperty<>(date);
            this.time = new javafx.beans.property.SimpleStringProperty(time);
            this.vehicleCount = new javafx.beans.property.SimpleIntegerProperty(vehicleCount);
            this.weather = new javafx.beans.property.SimpleStringProperty(weather);
            this.roadCondition = new javafx.beans.property.SimpleStringProperty(roadCondition);
        }
        
        public javafx.beans.property.ObjectProperty<LocalDate> dateProperty() {
            return date;
        }
        
        public javafx.beans.property.StringProperty timeProperty() {
            return time;
        }
        
        public javafx.beans.property.IntegerProperty vehicleCountProperty() {
            return vehicleCount;
        }
        
        public javafx.beans.property.StringProperty weatherProperty() {
            return weather;
        }
        
        public javafx.beans.property.StringProperty roadConditionProperty() {
            return roadCondition;
        }
    }
}