package example;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.StructField;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class TrafficDashboard {

    private static final int CANVAS_WIDTH = 600;
    private static final int CANVAS_HEIGHT = 500;
    private static final int PADDING = 15;

    private static SparkSession spark;
    private static Dataset<Row> trafficDataFrame;
    private static Map<String, Integer> trafficData = new HashMap<>();
    private static Map<String, Map<String, Integer>> timeSeriesData = new HashMap<>();
    private static Map<String, Double> trafficTrends = new HashMap<>();
    private static List<String> timeSlots = new ArrayList<>();
    private static List<String> dateSlots = new ArrayList<>();
    private static Label lastUpdatedLabel;
    private static Label totalVehiclesLabel;
    private static Label averageVehiclesLabel;
    private static Label maxCongestionLabel;
    
    public static void show(Stage primaryStage) {
        // Initialize Spark session
        initializeSparkSession();

        // Main layout
        BorderPane mainLayout = new BorderPane();
        mainLayout.setStyle("-fx-background-color: #f5f5f5;");
        
        // Header
        HBox header = createHeader();
        mainLayout.setTop(header);
        
        // Dashboard content with tabs
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        
        // Overview Tab with improved graphs
        Tab overviewTab = new Tab("Overview");
        overviewTab.setContent(createOverviewPanel());
        
        // Time Series Analysis Tab (replaces the old heatmap)
        Tab timeSeriesTab = new Tab("Time Series");
        timeSeriesTab.setContent(createTimeSeriesPanel());
        
        // Distribution Analysis Tab (enhanced analytics)
        Tab distributionTab = new Tab("Distribution");
        distributionTab.setContent(createDistributionPanel());
        
        // Comparison Tab (new)
        Tab comparisonTab = new Tab("Junction Comparison");
        comparisonTab.setContent(createComparisonPanel());
        
        // Add tabs to tabPane
        tabPane.getTabs().addAll(overviewTab, timeSeriesTab, distributionTab, comparisonTab);
        mainLayout.setCenter(tabPane);
        
        // Footer with actions
        HBox footer = createFooter(primaryStage);
        mainLayout.setBottom(footer);
        
        // Scene setup
        Scene scene = new Scene(mainLayout, 1000, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Traffic Analysis Dashboard");
        primaryStage.show();
        
        // Load initial data
        Platform.runLater(() -> {
            try {
                Thread.sleep(500);
                updateDashboard(primaryStage);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        });
    }
    
    private static HBox createHeader() {
        Label title = new Label("🚦 Traffic Analytics Dashboard");
        title.setFont(Font.font("System", FontWeight.BOLD, 28));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        lastUpdatedLabel = new Label("Last updated: Never");
        lastUpdatedLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px;");
        
        VBox titleBox = new VBox(5, title, lastUpdatedLabel);
        
        HBox header = new HBox(20, titleBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(15, 20, 15, 20));
        header.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 0 0 1 0;");
        
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        
        return header;
    }
    
    private static ScrollPane createOverviewPanel() {
        // Create dashboard metrics
        GridPane metricsGrid = new GridPane();
        metricsGrid.setHgap(20);
        metricsGrid.setVgap(20);
        metricsGrid.setPadding(new Insets(10));
        metricsGrid.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        // Total Vehicles Metric
        VBox totalVehiclesBox = createMetricBox("Total Vehicles", "Loading...", "🚗", "#3498db");
        totalVehiclesLabel = (Label) totalVehiclesBox.getChildren().get(1);
        metricsGrid.add(totalVehiclesBox, 0, 0);
        
        // Average Vehicles Metric
        VBox avgVehiclesBox = createMetricBox("Average per Junction", "Loading...", "📊", "#2ecc71");
        averageVehiclesLabel = (Label) avgVehiclesBox.getChildren().get(1);
        metricsGrid.add(avgVehiclesBox, 1, 0);
        
        // Max Congestion Metric
        VBox maxCongestionBox = createMetricBox("Most Congested", "Loading...", "🔥", "#e74c3c");
        maxCongestionLabel = (Label) maxCongestionBox.getChildren().get(1);
        metricsGrid.add(maxCongestionBox, 2, 0);
        
        // Improved title and description for first chart
        Label topJunctionsTitle = new Label("Top Congested Junctions");
        topJunctionsTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        topJunctionsTitle.setStyle("-fx-text-fill: #2c3e50;");
        
        Label topJunctionsDescription = new Label("Traffic volume at the 8 most congested junctions");
        topJunctionsDescription.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create horizontal bar chart for top junctions (this is more informative than the pie chart)
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Vehicles");
        yAxis.setLabel("Junction Location");
        
        BarChart<Number, String> topJunctionsChart = new BarChart<>(yAxis, xAxis);
        topJunctionsChart.setTitle("");
        topJunctionsChart.setLegendVisible(false);
        topJunctionsChart.setPrefHeight(300);
        
        // Congestion trend title and description
        Label trendTitle = new Label("Traffic Congestion Distribution");
        trendTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        trendTitle.setStyle("-fx-text-fill: #2c3e50;");
        
        Label trendDescription = new Label("Number of junctions by traffic volume range");
        trendDescription.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create a distribution chart (replaces pie chart with more informative visualization)
        CategoryAxis categoryAxis = new CategoryAxis();
        NumberAxis countAxis = new NumberAxis();
        categoryAxis.setLabel("Traffic Volume Range");
        countAxis.setLabel("Number of Junctions");
        
        BarChart<String, Number> distributionChart = new BarChart<>(categoryAxis, countAxis);
        distributionChart.setTitle("");
        distributionChart.setLegendVisible(false);
        distributionChart.setPrefHeight(300);
        
        // Container for first chart
        VBox topJunctionsContainer = new VBox(8, topJunctionsTitle, topJunctionsDescription, topJunctionsChart);
        topJunctionsContainer.setPadding(new Insets(20));
        topJunctionsContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        // Container for second chart
        VBox trendContainer = new VBox(8, trendTitle, trendDescription, distributionChart);
        trendContainer.setPadding(new Insets(20));
        trendContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox mainContent = new VBox(20, metricsGrid, topJunctionsContainer, trendContainer);
        mainContent.setPadding(new Insets(20));
        
        ScrollPane scrollPane = new ScrollPane(mainContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        return scrollPane;
    }
    
    private static ScrollPane createTimeSeriesPanel() {
        Label title = new Label("Traffic Volume Over Time");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        Label subtitle = new Label("Analyze how traffic changes throughout different time periods");
        subtitle.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create time series chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time Period");
        yAxis.setLabel("Average Vehicle Count");
        
        LineChart<String, Number> timeSeriesChart = new LineChart<>(xAxis, yAxis);
        timeSeriesChart.setTitle("Traffic Volume Trends");
        timeSeriesChart.setPrefHeight(400);
        timeSeriesChart.setCreateSymbols(true);
        timeSeriesChart.setAnimated(true);
        
        // Junction selection for time series
        Label junctionLabel = new Label("Select Junction:");
        junctionLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        ComboBox<String> junctionCombo = new ComboBox<>();
        junctionCombo.setPrefWidth(250);
        junctionCombo.setPromptText("Select a junction to analyze");
        
        // Add listener to update chart when junction is selected
        junctionCombo.setOnAction(e -> {
            updateTimeSeriesChart(timeSeriesChart, junctionCombo.getValue());
        });
        
        HBox junctionSelector = new HBox(10, junctionLabel, junctionCombo);
        junctionSelector.setAlignment(Pos.CENTER_LEFT);
        
        // Peak hours analysis chart
        Label peakHoursTitle = new Label("Peak Hours Analysis");
        peakHoursTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        peakHoursTitle.setStyle("-fx-text-fill: #2c3e50;");
        
        Label peakHoursSubtitle = new Label("Traffic volumes across different time periods (averaged across all junctions)");
        peakHoursSubtitle.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create bar chart for peak hours
        CategoryAxis timeAxis = new CategoryAxis();
        NumberAxis volumeAxis = new NumberAxis();
        timeAxis.setLabel("Time Period");
        volumeAxis.setLabel("Average Vehicle Count");
        
        BarChart<String, Number> peakHoursChart = new BarChart<>(timeAxis, volumeAxis);
        peakHoursChart.setTitle("");
        peakHoursChart.setPrefHeight(350);
        peakHoursChart.setLegendVisible(false);
        
        VBox timeSeriesContainer = new VBox(10, title, subtitle, junctionSelector, timeSeriesChart);
        timeSeriesContainer.setPadding(new Insets(20));
        timeSeriesContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox peakHoursContainer = new VBox(10, peakHoursTitle, peakHoursSubtitle, peakHoursChart);
        peakHoursContainer.setPadding(new Insets(20));
        peakHoursContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox containerBox = new VBox(20, timeSeriesContainer, peakHoursContainer);
        containerBox.setPadding(new Insets(20));
        
        ScrollPane scrollPane = new ScrollPane(containerBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        return scrollPane;
    }
    
    private static ScrollPane createDistributionPanel() {
        Label title = new Label("Traffic Distribution Analysis");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        Label subtitle = new Label("Statistical distribution of traffic across junctions");
        subtitle.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create scatter plot for distribution analysis
        NumberAxis xAxis = new NumberAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Junction ID");
        yAxis.setLabel("Vehicle Count");
        
        ScatterChart<Number, Number> distributionChart = new ScatterChart<>(xAxis, yAxis);
        distributionChart.setTitle("Traffic Volume Distribution");
        distributionChart.setPrefHeight(400);
        
        // Trend analysis chart - shows how congestion has changed over time
        Label trendTitle = new Label("Traffic Congestion Categories");
        trendTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        trendTitle.setStyle("-fx-text-fill: #2c3e50;");
        
        Label trendSubtitle = new Label("Percentage of junctions in each congestion category");
        trendSubtitle.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create stacked bar chart for trend analysis
        CategoryAxis categoryAxis = new CategoryAxis();
        NumberAxis percentAxis = new NumberAxis();
        categoryAxis.setLabel("Congestion Category");
        percentAxis.setLabel("Percentage of Junctions");
        
        // Use stacked bar chart to show proportion of junctions in each category
        StackedBarChart<String, Number> categoryChart = new StackedBarChart<>(categoryAxis, percentAxis);
        categoryChart.setTitle("");
        categoryChart.setPrefHeight(350);
        
        VBox distributionContainer = new VBox(10, title, subtitle, distributionChart);
        distributionContainer.setPadding(new Insets(20));
        distributionContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox trendContainer = new VBox(10, trendTitle, trendSubtitle, categoryChart);
        trendContainer.setPadding(new Insets(20));
        trendContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox containerBox = new VBox(20, distributionContainer, trendContainer);
        containerBox.setPadding(new Insets(20));
        
        ScrollPane scrollPane = new ScrollPane(containerBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        return scrollPane;
    }
    
    private static ScrollPane createComparisonPanel() {
        Label title = new Label("Junction Comparison");
        title.setFont(Font.font("System", FontWeight.BOLD, 18));
        title.setStyle("-fx-text-fill: #2c3e50;");
        
        Label subtitle = new Label("Compare traffic patterns between different junctions");
        subtitle.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        // Create junction selectors
        Label junction1Label = new Label("Junction 1:");
        junction1Label.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        ComboBox<String> junction1Combo = new ComboBox<>();
        junction1Combo.setPrefWidth(250);
        junction1Combo.setPromptText("Select first junction");
        
        Label junction2Label = new Label("Junction 2:");
        junction2Label.setFont(Font.font("System", FontWeight.BOLD, 14));
        
        ComboBox<String> junction2Combo = new ComboBox<>();
        junction2Combo.setPrefWidth(250);
        junction2Combo.setPromptText("Select second junction");
        
        Button compareButton = new Button("Compare");
        compareButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        
        HBox junction1Selector = new HBox(10, junction1Label, junction1Combo);
        junction1Selector.setAlignment(Pos.CENTER_LEFT);
        
        HBox junction2Selector = new HBox(10, junction2Label, junction2Combo);
        junction2Selector.setAlignment(Pos.CENTER_LEFT);
        
        VBox selectors = new VBox(10, junction1Selector, junction2Selector, compareButton);
        selectors.setPadding(new Insets(10, 0, 20, 0));
        
        // Create comparison chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Time Period");
        yAxis.setLabel("Vehicle Count");
        
        LineChart<String, Number> comparisonChart = new LineChart<>(xAxis, yAxis);
        comparisonChart.setTitle("Junction Traffic Comparison Over Time");
        comparisonChart.setPrefHeight(400);
        comparisonChart.setCreateSymbols(true);
        comparisonChart.setAnimated(true);
        
        // Set up comparison action
        compareButton.setOnAction(e -> {
            String junction1 = junction1Combo.getValue();
            String junction2 = junction2Combo.getValue();
            
            if (junction1 != null && junction2 != null) {
                updateComparisonChart(comparisonChart, junction1, junction2);
            }
        });
        
        // Area chart showing overall traffic pattern
        Label patternTitle = new Label("Overall Traffic Pattern");
        patternTitle.setFont(Font.font("System", FontWeight.BOLD, 18));
        patternTitle.setStyle("-fx-text-fill: #2c3e50;");
        
        Label patternSubtitle = new Label("Hourly traffic volume trend aggregated across all junctions");
        patternSubtitle.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px;");
        
        CategoryAxis timeAxis = new CategoryAxis();
        NumberAxis volumeAxis = new NumberAxis();
        timeAxis.setLabel("Time of Day");
        volumeAxis.setLabel("Average Vehicle Count");
        
        AreaChart<String, Number> patternChart = new AreaChart<>(timeAxis, volumeAxis);
        patternChart.setTitle("");
        patternChart.setPrefHeight(350);
        
        VBox comparisonContainer = new VBox(10, title, subtitle, selectors, comparisonChart);
        comparisonContainer.setPadding(new Insets(20));
        comparisonContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox patternContainer = new VBox(10, patternTitle, patternSubtitle, patternChart);
        patternContainer.setPadding(new Insets(20));
        patternContainer.setStyle("-fx-background-color: white; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        VBox containerBox = new VBox(20, comparisonContainer, patternContainer);
        containerBox.setPadding(new Insets(20));
        
        ScrollPane scrollPane = new ScrollPane(containerBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #f5f5f5;");
        
        return scrollPane;
    }
    
    private static VBox createMetricBox(String title, String value, String icon, String color) {
        Label titleLabel = new Label(icon + " " + title);
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        titleLabel.setStyle("-fx-text-fill: #7f8c8d;");
        
        Label valueLabel = new Label(value);
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 24));
        valueLabel.setStyle("-fx-text-fill: " + color + ";");
        
        VBox metricBox = new VBox(5, titleLabel, valueLabel);
        metricBox.setPadding(new Insets(15));
        metricBox.setMinWidth(200);
        metricBox.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; " +
                "-fx-border-width: 1; -fx-border-radius: 5; -fx-background-radius: 5;");
        
        return metricBox;
    }
    
    private static HBox createFooter(Stage primaryStage) {
        Button refreshButton = new Button("🔄 Refresh Data");
        refreshButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8px 16px;");
        refreshButton.setOnAction(e -> updateDashboard(primaryStage));
        
        Button exportButton = new Button("📊 Export Report");
        exportButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8px 16px;");
        
        Button backButton = new Button("⬅ Back to Home");
        backButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 14px; -fx-padding: 8px 16px;");
        backButton.setOnAction(e -> HomePage.show(primaryStage));
        
        HBox footer = new HBox(15, refreshButton, exportButton, backButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(15, 20, 15, 20));
        footer.setStyle("-fx-background-color: white; -fx-border-color: #e0e0e0; -fx-border-width: 1 0 0 0;");
        
        return footer;
    }
    
    private static void initializeSparkSession() {
        if (spark == null) {
            System.out.println("⚠️ Creating new Spark session...");
            try {
                spark = SparkSession.builder()
                        .appName("Traffic Dashboard")
                        .master("local[*]")
                        .getOrCreate();
                System.out.println("✅ Spark session created successfully");
            } catch (Exception e) {
                System.err.println("❌ Failed to create Spark session: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private static void updateDashboard(Stage stage) {
        System.out.println("⚠️ Starting dashboard update...");
        
        if (spark == null) {
            System.err.println("❌ Spark session is not initialized!");
            return;
        }

        // Try using a relative path first
        String filePath = "final_cleaned_pune_traffic.csv";
        File file = new File(filePath);
        
        // Try user directory if relative path doesn't work
        if (!file.exists()) {
            try {
                String userDir = System.getProperty("user.dir");
                filePath = userDir + File.separator + "final_cleaned_pune_traffic.csv";
                file = new File(filePath);
                
                // If that doesn't work, try the absolute path
                if (!file.exists()) {
                    filePath = "D:" + File.separator + "3rd year" + File.separator + "6th sem" + 
                              File.separator + "BDT" + File.separator + "Mini Project" + 
                              File.separator + "final_cleaned_pune_traffic.csv";
                    file = new File(filePath);
                }
            } catch (Exception e) {
                System.err.println("❌ Error creating file path: " + e.getMessage());
            }
        }

        System.out.println("⚠️ Looking for file at: " + file.getAbsolutePath());
        
        // If file still not found, show file chooser dialog
        if (!file.exists()) {
            System.err.println("❌ File not found. Showing file chooser dialog");
            Platform.runLater(() -> showFileChooser(stage));
            return;
        }

        // Process the file if it exists
        processFile(file.getAbsolutePath());
    }
    
    private static void showFileChooser(Stage stage) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Select Traffic Data CSV File");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv")
            );
            File selectedFile = fileChooser.showOpenDialog(stage);
            
            if (selectedFile != null) {
                String selectedPath = selectedFile.getAbsolutePath();
                System.out.println("⚠️ User selected file: " + selectedPath);
                
                // Process the selected file
                processFile(selectedPath);
            }
        } catch (Exception e) {
            System.err.println("❌ Error showing file chooser: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void processFile(String filePath) {
        // Create a separate thread for Spark operations
        new Thread(() -> {
            try {
                // Load CSV into DataFrame
                System.out.println("⚠️ Loading CSV data from: " + filePath);
                trafficDataFrame = spark.read()
                        .format("csv")
                        .option("header", "true")
                        .option("inferSchema", "true")
                        .load(filePath);

                System.out.println("✅ DataFrame loaded successfully");
                System.out.println("⚠️ DataFrame Schema:");
                trafficDataFrame.printSchema();
                
                // Show a preview of the data
                System.out.println("⚠️ DataFrame Preview:");
                trafficDataFrame.show(5);
                
                // Get column names for better error handling
                String[] columns = trafficDataFrame.columns();
                System.out.println("⚠️ Available columns: " + String.join(", ", columns));
                
                // Determine the actual column names (case-insensitive)
                String junctionCol = findColumnCaseInsensitive(columns, "Junction");
                String vehiclesCol = findColumnCaseInsensitive(columns, "Vehicles");
                String timeCol = findColumnCaseInsensitive(columns, "Time");
                String dateCol = findColumnCaseInsensitive(columns, "Date");
                
                if (junctionCol == null || vehiclesCol == null) {
                    String errorMsg = "Required columns not found. Need 'Junction' and 'Vehicles'. Found: " + 
                        String.join(", ", columns);
                    System.err.println("❌ " + errorMsg);
                    return;
                }

                // Convert data to Map (Junction -> Vehicles Count)
                trafficData = new HashMap<>();
                timeSeriesData = new HashMap<>();
                List<Row> rows = trafficDataFrame.collectAsList();
                
                // Extract time slots if time column exists
                if (timeCol != null) {
                    timeSlots = rows.stream()
                        .map(row -> {
                            try { 
                                Object timeValue = row.getAs(timeCol);
                                return timeValue != null ? timeValue.toString() : null;
                            } 
                            catch (Exception e) { 
                                System.err.println("Error extracting time: " + e.getMessage());
                                return null; 
                            }
                        })
                        .filter(time -> time != null)
                        .distinct()
                        .sorted()
                        .collect(Collectors.toList());
                    
                    System.out.println("Found " + timeSlots.size() + " distinct time slots");
                }
                
                // Extract date slots if date column exists
                if (dateCol != null) {
                    dateSlots = rows.stream()
                        .map(row -> {
                            try { 
                                Object dateValue = row.getAs(dateCol);
                                return dateValue != null ? dateValue.toString() : null;
                            } 
                            catch (Exception e) { 
                                System.err.println("Error extracting date: " + e.getMessage());
                                return null; 	
                            }
                        })

            .filter(date -> date != null)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        System.out.println("Found " + dateSlots.size() + " distinct date slots");
    }

    // Process all rows
    for (Row row : rows) {
        try {
            // Extract junction and vehicle count
            String junction = row.getAs(junctionCol).toString();
            int vehicles = Integer.parseInt(row.getAs(vehiclesCol).toString());
            
            // Add to main traffic data
            trafficData.put(junction, vehicles);
            
            // Process time series data if time column exists
            if (timeCol != null) {
                String time = row.getAs(timeCol).toString();
                
                // Initialize map for this junction if it doesn't exist
                if (!timeSeriesData.containsKey(junction)) {
                    timeSeriesData.put(junction, new HashMap<>());
                }
                
                // Add time entry
                timeSeriesData.get(junction).put(time, vehicles);
            }
        } catch (Exception e) {
            System.err.println("Error processing row: " + e.getMessage());
        }
    }
    
    // Calculate traffic trends as percentage change
    if (timeCol != null && !timeSlots.isEmpty() && timeSlots.size() > 1) {
        for (String junction : trafficData.keySet()) {
            if (timeSeriesData.containsKey(junction)) {
                Map<String, Integer> junctionTimeSeries = timeSeriesData.get(junction);
                
                if (junctionTimeSeries.size() > 1) {
                    String firstTime = timeSlots.get(0);
                    String lastTime = timeSlots.get(timeSlots.size() - 1);
                    
                    if (junctionTimeSeries.containsKey(firstTime) && junctionTimeSeries.containsKey(lastTime)) {
                        int firstCount = junctionTimeSeries.get(firstTime);
                        int lastCount = junctionTimeSeries.get(lastTime);
                        
                        if (firstCount > 0) {
                            double percentChange = ((lastCount - firstCount) / (double) firstCount) * 100;
                            trafficTrends.put(junction, percentChange);
                        }
                    }
                }
            }
        }
    }
    
    // Update the UI with processed data
    Platform.runLater(() -> {
        try {
            updateOverviewCharts();
            updateTimeSeriesCharts();
            updateDistributionCharts();
            updateComparisonCharts();
            updateMetrics();
            
            // Update last updated time
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            lastUpdatedLabel.setText("Last updated: " + dateFormat.format(new Date()));
        } catch (Exception e) {
            System.err.println("Error updating UI: " + e.getMessage());
            e.printStackTrace();
        }
    });
    
    System.out.println("✅ Dashboard update completed successfully");
    
} catch (Exception e) {
    System.err.println("❌ Error processing data: " + e.getMessage());
    e.printStackTrace();
}
}).start();
}

private static String findColumnCaseInsensitive(String[] columns, String targetColumn) {
for (String column : columns) {
if (column.equalsIgnoreCase(targetColumn)) {
    return column;
}
}
return null;
}

private static void updateMetrics() {
if (trafficData.isEmpty()) {
System.err.println("No traffic data available to update metrics");
return;
}

// Calculate total vehicles
int totalVehicles = trafficData.values().stream().mapToInt(Integer::intValue).sum();
totalVehiclesLabel.setText(String.format("%,d", totalVehicles));

// Calculate average vehicles per junction
double averageVehicles = trafficData.values().stream().mapToInt(Integer::intValue).average().orElse(0);
averageVehiclesLabel.setText(String.format("%.1f", averageVehicles));

// Find most congested junction
String mostCongested = trafficData.entrySet().stream()
    .max(Comparator.comparingInt(Map.Entry::getValue))
    .map(Map.Entry::getKey)
    .orElse("None");
maxCongestionLabel.setText(mostCongested);
}

private static void updateOverviewCharts() {
if (trafficData.isEmpty()) {
System.err.println("No traffic data available to update overview charts");
return;
}

try {
// Sort traffic data by vehicle count (descending)
Map<String, Integer> sortedData = trafficData.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
        .limit(8)
        .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
        ));

// Update top junctions chart (horizontal bar chart)
TabPane tabPane = (TabPane) totalVehiclesLabel.getScene().lookup("TabPane");
Tab overviewTab = tabPane.getTabs().get(0);
ScrollPane scrollPane = (ScrollPane) overviewTab.getContent();
VBox mainContent = (VBox) scrollPane.getContent();

// Get the container for the top junctions chart (third element in mainContent)
VBox topJunctionsContainer = (VBox) mainContent.getChildren().get(1);
BarChart<Number, String> topJunctionsChart = (BarChart<Number, String>) topJunctionsContainer.getChildren().get(2);

topJunctionsChart.getData().clear();

XYChart.Series<Number, String> series = new XYChart.Series<>();
series.setName("Vehicle Count");

for (Map.Entry<String, Integer> entry : sortedData.entrySet()) {
    series.getData().add(new XYChart.Data<>(entry.getValue(), entry.getKey()));
}

topJunctionsChart.getData().add(series);

// Apply styling to the bars
for (XYChart.Data<Number, String> data : series.getData()) {
    data.getNode().setStyle("-fx-bar-fill: #3498db;");
}

// Update distribution chart
VBox trendContainer = (VBox) mainContent.getChildren().get(2);
BarChart<String, Number> distributionChart = (BarChart<String, Number>) trendContainer.getChildren().get(2);

distributionChart.getData().clear();

// Create traffic volume ranges
int maxVehicles = trafficData.values().stream().mapToInt(Integer::intValue).max().orElse(0);
int rangeSize = maxVehicles / 5; // 5 ranges

// Count junctions in each range
Map<String, Integer> rangeDistribution = new LinkedHashMap<>();
rangeDistribution.put("0-" + rangeSize, 0);
rangeDistribution.put((rangeSize+1) + "-" + (2*rangeSize), 0);
rangeDistribution.put((2*rangeSize+1) + "-" + (3*rangeSize), 0);
rangeDistribution.put((3*rangeSize+1) + "-" + (4*rangeSize), 0);
rangeDistribution.put((4*rangeSize+1) + "+", 0);

for (int count : trafficData.values()) {
    if (count <= rangeSize) {
        rangeDistribution.put("0-" + rangeSize, rangeDistribution.get("0-" + rangeSize) + 1);
    } else if (count <= 2*rangeSize) {
        rangeDistribution.put((rangeSize+1) + "-" + (2*rangeSize), rangeDistribution.get((rangeSize+1) + "-" + (2*rangeSize)) + 1);
    } else if (count <= 3*rangeSize) {
        rangeDistribution.put((2*rangeSize+1) + "-" + (3*rangeSize), rangeDistribution.get((2*rangeSize+1) + "-" + (3*rangeSize)) + 1);
    } else if (count <= 4*rangeSize) {
        rangeDistribution.put((3*rangeSize+1) + "-" + (4*rangeSize), rangeDistribution.get((3*rangeSize+1) + "-" + (4*rangeSize)) + 1);
    } else {
        rangeDistribution.put((4*rangeSize+1) + "+", rangeDistribution.get((4*rangeSize+1) + "+") + 1);
    }
}

XYChart.Series<String, Number> rangeSeries = new XYChart.Series<>();
rangeSeries.setName("Number of Junctions");

for (Map.Entry<String, Integer> entry : rangeDistribution.entrySet()) {
    rangeSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
}

distributionChart.getData().add(rangeSeries);

// Apply styling to the bars with color gradient
String[] colors = {"#d1f3ff", "#a0deff", "#6fc7ff", "#4292ff", "#1c5eff"};
int colorIndex = 0;

for (XYChart.Data<String, Number> data : rangeSeries.getData()) {
    data.getNode().setStyle("-fx-bar-fill: " + colors[colorIndex++] + ";");
}
} catch (Exception e) {
System.err.println("Error updating overview charts: " + e.getMessage());
e.printStackTrace();
}
}

private static void updateTimeSeriesCharts() {
if (timeSeriesData.isEmpty() || timeSlots.isEmpty()) {
System.err.println("No time series data available to update charts");
return;
}

try {
// Get the time series tab
TabPane tabPane = (TabPane) totalVehiclesLabel.getScene().lookup("TabPane");
Tab timeSeriesTab = tabPane.getTabs().get(1);
ScrollPane scrollPane = (ScrollPane) timeSeriesTab.getContent();
VBox containerBox = (VBox) scrollPane.getContent();

// Get the time series container
VBox timeSeriesContainer = (VBox) containerBox.getChildren().get(0);

// Get the junction combo box
HBox junctionSelector = (HBox) timeSeriesContainer.getChildren().get(2);
ComboBox<String> junctionCombo = (ComboBox<String>) junctionSelector.getChildren().get(1);

// Update junction combo box with available junctions
junctionCombo.getItems().clear();
junctionCombo.getItems().addAll(timeSeriesData.keySet());

// If there's a previously selected junction, try to select it again
if (junctionCombo.getValue() != null && timeSeriesData.containsKey(junctionCombo.getValue())) {
    String selectedJunction = junctionCombo.getValue();
    junctionCombo.setValue(selectedJunction);
    
    // Update the chart with the selected junction
    LineChart<String, Number> timeSeriesChart = (LineChart<String, Number>) timeSeriesContainer.getChildren().get(3);
    updateTimeSeriesChart(timeSeriesChart, selectedJunction);
} else if (!junctionCombo.getItems().isEmpty()) {
    // Otherwise select the first junction
    junctionCombo.setValue(junctionCombo.getItems().get(0));
}

// Update peak hours chart
VBox peakHoursContainer = (VBox) containerBox.getChildren().get(1);
BarChart<String, Number> peakHoursChart = (BarChart<String, Number>) peakHoursContainer.getChildren().get(2);

peakHoursChart.getData().clear();

// Calculate average vehicle count for each time slot across all junctions
Map<String, Double> peakHoursData = new TreeMap<>();

for (String timeSlot : timeSlots) {
    List<Integer> countsAtTime = new ArrayList<>();
    
    for (Map<String, Integer> junctionData : timeSeriesData.values()) {
        if (junctionData.containsKey(timeSlot)) {
            countsAtTime.add(junctionData.get(timeSlot));
        }
    }
    
    if (!countsAtTime.isEmpty()) {
        double average = countsAtTime.stream().mapToInt(Integer::intValue).average().orElse(0);
        peakHoursData.put(timeSlot, average);
    }
}

XYChart.Series<String, Number> peakSeries = new XYChart.Series<>();
peakSeries.setName("Average Traffic");

for (Map.Entry<String, Double> entry : peakHoursData.entrySet()) {
    peakSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
}

peakHoursChart.getData().add(peakSeries);

// Apply styling to the bars based on traffic volume
for (XYChart.Data<String, Number> data : peakSeries.getData()) {
    double value = data.getYValue().doubleValue();
    double maxValue = peakHoursData.values().stream().mapToDouble(Double::doubleValue).max().orElse(100);
    
    // Color gradient from green to red based on traffic volume
    int r = (int) Math.min(255, (value / maxValue) * 255);
    int g = (int) Math.min(255, 255 - (value / maxValue) * 128);
    int b = 100;
    
    String color = String.format("rgb(%d, %d, %d)", r, g, b);
    data.getNode().setStyle("-fx-bar-fill: " + color + ";");
}
} catch (Exception e) {
System.err.println("Error updating time series charts: " + e.getMessage());
e.printStackTrace();
}
}

private static void updateTimeSeriesChart(LineChart<String, Number> chart, String junction) {
if (chart == null || junction == null || !timeSeriesData.containsKey(junction)) {
return;
}

chart.getData().clear();

Map<String, Integer> junctionData = timeSeriesData.get(junction);
XYChart.Series<String, Number> series = new XYChart.Series<>();
series.setName(junction);

for (String timeSlot : timeSlots) {
if (junctionData.containsKey(timeSlot)) {
    series.getData().add(new XYChart.Data<>(timeSlot, junctionData.get(timeSlot)));
}
}

chart.getData().add(series);

// Customize series appearance
series.getNode().setStyle("-fx-stroke: #3498db; -fx-stroke-width: 3px;");

// Customize data points
for (XYChart.Data<String, Number> data : series.getData()) {
data.getNode().setStyle(
    "-fx-background-color: #3498db, white; " +
    "-fx-background-radius: 6px; " +
    "-fx-padding: 6px;"
);
}
}

private static void updateDistributionCharts() {
if (trafficData.isEmpty()) {
System.err.println("No traffic data available to update distribution charts");
return;
}

try {
// Get the distribution tab
TabPane tabPane = (TabPane) totalVehiclesLabel.getScene().lookup("TabPane");
Tab distributionTab = tabPane.getTabs().get(2);
ScrollPane scrollPane = (ScrollPane) distributionTab.getContent();
VBox containerBox = (VBox) scrollPane.getContent();

// Get the distribution container
VBox distributionContainer = (VBox) containerBox.getChildren().get(0);
ScatterChart<Number, Number> distributionChart = (ScatterChart<Number, Number>) distributionContainer.getChildren().get(2);

distributionChart.getData().clear();

// Create scatter plot data
XYChart.Series<Number, Number> series = new XYChart.Series<>();
series.setName("Traffic Distribution");

int index = 0;
for (Map.Entry<String, Integer> entry : trafficData.entrySet()) {
    series.getData().add(new XYChart.Data<>(index++, entry.getValue()));
}

distributionChart.getData().add(series);

// Customize data points
for (XYChart.Data<Number, Number> data : series.getData()) {
    data.getNode().setStyle(
        "-fx-background-color: rgba(52, 152, 219, 0.7); " +
        "-fx-background-radius: 5px; " +
        "-fx-padding: 5px;"
    );
}

// Update congestion categories chart
VBox categoryContainer = (VBox) containerBox.getChildren().get(1);
StackedBarChart<String, Number> categoryChart = (StackedBarChart<String, Number>) categoryContainer.getChildren().get(2);

categoryChart.getData().clear();

// Define congestion categories
int totalJunctions = trafficData.size();
int maxVehicles = trafficData.values().stream().mapToInt(Integer::intValue).max().orElse(0);

// Calculate quartiles
List<Integer> sortedValues = new ArrayList<>(trafficData.values());
Collections.sort(sortedValues);

int q1Index = sortedValues.size() / 4;
int q2Index = sortedValues.size() / 2;
int q3Index = 3 * sortedValues.size() / 4;

int q1 = sortedValues.get(q1Index);
int q2 = sortedValues.get(q2Index);
int q3 = sortedValues.get(q3Index);

// Count junctions in each category
long lowCount = trafficData.values().stream().filter(v -> v <= q1).count();
long mediumLowCount = trafficData.values().stream().filter(v -> v > q1 && v <= q2).count();
long mediumHighCount = trafficData.values().stream().filter(v -> v > q2 && v <= q3).count();
long highCount = trafficData.values().stream().filter(v -> v > q3).count();

// Convert to percentages
double lowPercent = (double) lowCount / totalJunctions * 100;
double mediumLowPercent = (double) mediumLowCount / totalJunctions * 100;
double mediumHighPercent = (double) mediumHighCount / totalJunctions * 100;
double highPercent = (double) highCount / totalJunctions * 100;

// Create series for each category
XYChart.Series<String, Number> lowSeries = new XYChart.Series<>();
lowSeries.setName("Low Traffic (0-" + q1 + ")");
lowSeries.getData().add(new XYChart.Data<>("Congestion Distribution", lowPercent));

XYChart.Series<String, Number> mediumLowSeries = new XYChart.Series<>();
mediumLowSeries.setName("Medium-Low (" + (q1+1) + "-" + q2 + ")");
mediumLowSeries.getData().add(new XYChart.Data<>("Congestion Distribution", mediumLowPercent));

XYChart.Series<String, Number> mediumHighSeries = new XYChart.Series<>();
mediumHighSeries.setName("Medium-High (" + (q2+1) + "-" + q3 + ")");
mediumHighSeries.getData().add(new XYChart.Data<>("Congestion Distribution", mediumHighPercent));

XYChart.Series<String, Number> highSeries = new XYChart.Series<>();
highSeries.setName("High Traffic (>" + q3 + ")");
highSeries.getData().add(new XYChart.Data<>("Congestion Distribution", highPercent));

categoryChart.getData().addAll(lowSeries, mediumLowSeries, mediumHighSeries, highSeries);

// Apply custom colors to each series
lowSeries.getNode().setStyle("-fx-fill: #2ecc71;"); // Green
mediumLowSeries.getNode().setStyle("-fx-fill: #f1c40f;"); // Yellow
mediumHighSeries.getNode().setStyle("-fx-fill: #e67e22;"); // Orange
highSeries.getNode().setStyle("-fx-fill: #e74c3c;"); // Red
} catch (Exception e) {
System.err.println("Error updating distribution charts: " + e.getMessage());
e.printStackTrace();
}
}

private static void updateComparisonCharts() {
if (timeSeriesData.isEmpty() || timeSlots.isEmpty()) {
System.err.println("No time series data available to update comparison charts");
return;
}

try {
// Get the comparison tab
TabPane tabPane = (TabPane) totalVehiclesLabel.getScene().lookup("TabPane");
Tab comparisonTab = tabPane.getTabs().get(3);
ScrollPane scrollPane = (ScrollPane) comparisonTab.getContent();
VBox containerBox = (VBox) scrollPane.getContent();

// Get the comparison container
VBox comparisonContainer = (VBox) containerBox.getChildren().get(0);
VBox selectors = (VBox) comparisonContainer.getChildren().get(2);

// Update junction combo boxes
HBox junction1Selector = (HBox) selectors.getChildren().get(0);
ComboBox<String> junction1Combo = (ComboBox<String>) junction1Selector.getChildren().get(1);

HBox junction2Selector = (HBox) selectors.getChildren().get(1);
ComboBox<String> junction2Combo = (ComboBox<String>) junction2Selector.getChildren().get(1);

// Clear and update junction lists
junction1Combo.getItems().clear();
junction2Combo.getItems().clear();

junction1Combo.getItems().addAll(timeSeriesData.keySet());
junction2Combo.getItems().addAll(timeSeriesData.keySet());

// Select first two junctions by default if we have at least two
if (junction1Combo.getItems().size() >= 2) {
    junction1Combo.setValue(junction1Combo.getItems().get(0));
    junction2Combo.setValue(junction1Combo.getItems().get(1));
} else if (junction1Combo.getItems().size() == 1) {
    junction1Combo.setValue(junction1Combo.getItems().get(0));
    junction2Combo.setValue(junction1Combo.getItems().get(0));
}

// Update overall traffic pattern area chart
VBox patternContainer = (VBox) containerBox.getChildren().get(1);
AreaChart<String, Number> patternChart = (AreaChart<String, Number>) patternContainer.getChildren().get(2);

patternChart.getData().clear();

XYChart.Series<String, Number> patternSeries = new XYChart.Series<>();
patternSeries.setName("Overall Traffic Pattern");

// Calculate average traffic pattern across all junctions
Map<String, Double> overallPattern = new TreeMap<>();

for (String timeSlot : timeSlots) {
    List<Integer> countsAtTime = new ArrayList<>();
    
    for (Map<String, Integer> junctionData : timeSeriesData.values()) {
        if (junctionData.containsKey(timeSlot)) {
            countsAtTime.add(junctionData.get(timeSlot));
        }
    }
    
    if (!countsAtTime.isEmpty()) {
        double average = countsAtTime.stream().mapToInt(Integer::intValue).average().orElse(0);
        overallPattern.put(timeSlot, average);
    }
}

for (Map.Entry<String, Double> entry : overallPattern.entrySet()) {
    patternSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
}

patternChart.getData().add(patternSeries);

// Apply gradient fill to area chart
patternSeries.getNode().lookup(".chart-series-area-fill").setStyle(
    "-fx-fill: linear-gradient(to bottom, rgba(52, 152, 219, 0.7), rgba(52, 152, 219, 0.1));"
);
patternSeries.getNode().lookup(".chart-series-area-line").setStyle(
    "-fx-stroke: #3498db; -fx-stroke-width: 2px;"
);

// If two junctions are selected, update comparison chart
if (junction1Combo.getValue() != null && junction2Combo.getValue() != null) {
    LineChart<String, Number> comparisonChart = (LineChart<String, Number>) comparisonContainer.getChildren().get(3);
    updateComparisonChart(comparisonChart, junction1Combo.getValue(), junction2Combo.getValue());
}
} catch (Exception e) {
System.err.println("Error updating comparison charts: " + e.getMessage());
e.printStackTrace();
}
}

private static void updateComparisonChart(LineChart<String, Number> chart, String junction1, String junction2) {
if (chart == null || junction1 == null || junction2 == null || 
!timeSeriesData.containsKey(junction1) || !timeSeriesData.containsKey(junction2)) {
return;
}

chart.getData().clear();

// Create series for junction1
Map<String, Integer> junction1Data = timeSeriesData.get(junction1);
XYChart.Series<String, Number> series1 = new XYChart.Series<>();
series1.setName(junction1);

for (String timeSlot : timeSlots) {
if (junction1Data.containsKey(timeSlot)) {
    series1.getData().add(new XYChart.Data<>(timeSlot, junction1Data.get(timeSlot)));
}
}

// Create series for junction2
Map<String, Integer> junction2Data = timeSeriesData.get(junction2);
XYChart.Series<String, Number> series2 = new XYChart.Series<>();
series2.setName(junction2);

for (String timeSlot : timeSlots) {
if (junction2Data.containsKey(timeSlot)) {
    series2.getData().add(new XYChart.Data<>(timeSlot, junction2Data.get(timeSlot)));
}
}

chart.getData().addAll(series1, series2);

// Customize series appearance
series1.getNode().setStyle("-fx-stroke: #3498db; -fx-stroke-width: 3px;");
series2.getNode().setStyle("-fx-stroke: #e74c3c; -fx-stroke-width: 3px;");

// Customize data points
for (XYChart.Data<String, Number> data : series1.getData()) {
data.getNode().setStyle(
    "-fx-background-color: #3498db, white; " +
    "-fx-background-radius: 6px; " +
    "-fx-padding: 5px;"
);
}

for (XYChart.Data<String, Number> data : series2.getData()) {
data.getNode().setStyle(
    "-fx-background-color: #e74c3c, white; " +
    "-fx-background-radius: 6px; " +
    "-fx-padding: 5px;"
);
}
}
}