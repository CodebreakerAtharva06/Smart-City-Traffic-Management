package example;

import org.apache.spark.sql.*;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.ml.classification.LogisticRegression;
import org.apache.spark.ml.classification.LogisticRegressionModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.feature.StringIndexer;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.ml.feature.IndexToString;
import org.apache.spark.ml.feature.OneHotEncoder;
import org.apache.spark.ml.Pipeline;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.PipelineStage;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.sql.types.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.effect.DropShadow;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.*;

public class MLPredictedAnalysisPage {

    private final SparkSession spark;
    private Dataset<Row> trafficData;
    private LinearRegressionModel vehicleModel;
    private PipelineModel roadConditionModel;
    private StringIndexerModel junctionIndexer;
    private StringIndexerModel roadConditionIndexer;
    private List<String> roadConditionCategories;
    private TextArea resultArea;
    private boolean modelsReady = false;

    // UI components
    private ComboBox<String> junctionComboBox;
    private DatePicker datePicker;
    private ComboBox<Integer> hourComboBox;
    private Button predictButton;
    private VBox rootPane;

    public MLPredictedAnalysisPage() {
        // Use the AppManager to get the SparkSession instead of creating a new one
        this.spark = AppManager.getSparkSession();

        try {
            // Get data from AppManager with default path
            String filePath = getDatasetPath();
            if (filePath != null) {
                System.out.println("Attempting to load dataset from: " + filePath);
                this.trafficData = AppManager.getDataFrame(filePath);
                if (this.trafficData != null) {
                    System.out.println("Dataset loaded successfully. Column names: " +
                            String.join(", ", this.trafficData.columns()));

                    // Make sure the required columns are available
                    if (!validateColumns()) {
                        System.err.println("Dataset is missing required columns.");
                        return;
                    }

                    prepareAndTrainModels(); // Train models after loading data
                } else {
                    System.err.println("Error: Dataset could not be loaded.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error initializing MLPredictedAnalysisPage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getDatasetPath() {
        String filePath = "D:/3rd year/6th sem/BDT/Mini Project/final_cleaned_pune_traffic.csv";
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("Dataset file not found at: " + filePath);
            // Let AppManager try to find the file
            return filePath;
        }
        return filePath;
    }

    private boolean validateColumns() {
        if (trafficData == null) return false;

        // List required columns
        List<String> requiredColumns = Arrays.asList("Junction", "Date", "Hour", "Vehicles", "RoadCondition");
        List<String> missingColumns = new ArrayList<>();

        // Check which required columns are missing
        for (String column : requiredColumns) {
            if (!Arrays.asList(trafficData.columns()).contains(column)) {
                missingColumns.add(column);
            }
        }

        if (!missingColumns.isEmpty()) {
            System.err.println("Missing required columns: " + String.join(", ", missingColumns));
            return false;
        }

        return true;
    }

    private void prepareAndTrainModels() {
        if (trafficData == null) {
            System.err.println("No data available for training.");
            return;
        }

        // Add a try-catch block to handle potential errors during model training
        try {
            trainVehicleModel();
            trainRoadConditionModel();
            modelsReady = true;
            System.out.println("All ML models trained successfully.");
        } catch (Exception e) {
            System.err.println("Error training models: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void trainVehicleModel() {
        try {
            // Create and save StringIndexers to member variables for later use in prediction
            StringIndexer junctionIndexerObj = new StringIndexer()
                    .setInputCol("Junction")
                    .setOutputCol("JunctionIndex")
                    .setHandleInvalid("keep");
            this.junctionIndexer = junctionIndexerObj.fit(trafficData);

            OneHotEncoder junctionEncoder = new OneHotEncoder()
                    .setInputCol("JunctionIndex")
                    .setOutputCol("JunctionVec");

            // Check if DayOfWeek column exists
            Dataset<Row> preparedData = trafficData;
            if (!Arrays.asList(trafficData.columns()).contains("DayOfWeek")) {
                preparedData = preparedData.withColumn("DayOfWeek",
                        date_format(to_date(col("Date"), "yyyy-MM-dd"), "u").cast("int"));
            }

            VectorAssembler assembler = new VectorAssembler()
                    .setInputCols(new String[]{"Hour", "DayOfWeek", "JunctionVec"})
                    .setOutputCol("features");

            Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{
                    junctionIndexer, junctionEncoder, assembler
            });

            Dataset<Row> processedData = pipeline.fit(preparedData).transform(preparedData);
            Dataset<Row> trainingData = processedData.select("features", "Vehicles").na().drop();

            if (trainingData.count() == 0) {
                System.err.println("Vehicle training data is empty after preprocessing.");
                return;
            }

            LinearRegression lr = new LinearRegression()
                    .setLabelCol("Vehicles")
                    .setFeaturesCol("features")
                    .setMaxIter(10)
                    .setRegParam(0.1);

            this.vehicleModel = lr.fit(trainingData);
            System.out.println("Vehicle count model trained successfully!");

        } catch (Exception e) {
            System.err.println("Error training vehicle model: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to be caught by the caller
        }
    }

    private void trainRoadConditionModel() {
        try {
            // Check if RoadCondition column exists
            if (!Arrays.asList(trafficData.columns()).contains("RoadCondition")) {
                System.err.println("RoadCondition column not found in dataset");
                return;
            }

            // Create and save StringIndexer for road condition
            StringIndexer roadConditionIndexerObj = new StringIndexer()
                    .setInputCol("RoadCondition")
                    .setOutputCol("RoadConditionIndex")
                    .setHandleInvalid("keep");
            this.roadConditionIndexer = roadConditionIndexerObj.fit(trafficData);

            // Save road condition categories for later reference
            Dataset<Row> distinctRoadConditions = trafficData.select("RoadCondition").distinct();
            this.roadConditionCategories = distinctRoadConditions.collectAsList()
                    .stream()
                    .map(row -> row.getString(0))
                    .collect(Collectors.toList());

            // Prepare features for road condition prediction
            StringIndexer junctionIndexer = new StringIndexer()
                    .setInputCol("Junction")
                    .setOutputCol("JunctionIndex")
                    .setHandleInvalid("keep");

            OneHotEncoder junctionEncoder = new OneHotEncoder()
                    .setInputCol("JunctionIndex")
                    .setOutputCol("JunctionVec");

            // Check if DayOfWeek column exists
            Dataset<Row> preparedData = trafficData;
            if (!Arrays.asList(trafficData.columns()).contains("DayOfWeek")) {
                preparedData = preparedData.withColumn("DayOfWeek",
                        date_format(to_date(col("Date"), "yyyy-MM-dd"), "u").cast("int"));
            }

            VectorAssembler assembler = new VectorAssembler()
                    .setInputCols(new String[]{"Hour", "DayOfWeek", "Vehicles", "JunctionVec"})
                    .setOutputCol("features");

            // Add IndexToString for converting predictions back to string labels
            IndexToString labelConverter = new IndexToString()
                    .setInputCol("prediction")
                    .setOutputCol("predictedRoadCondition")
                    .setLabels(roadConditionIndexer.labelsArray()[0]);

            // Create a logistic regression model
            LogisticRegression lr = new LogisticRegression()
                    .setLabelCol("RoadConditionIndex")
                    .setFeaturesCol("features")
                    .setMaxIter(10)
                    .setRegParam(0.1);

            // Build the pipeline
            Pipeline pipeline = new Pipeline().setStages(new PipelineStage[]{
                    junctionIndexer, junctionEncoder,
                    roadConditionIndexer, assembler,
                    lr, labelConverter
            });

            // Train the model
            Dataset<Row> trainingData = preparedData.na().drop();
            if (trainingData.count() == 0) {
                System.err.println("Road condition training data is empty after preprocessing.");
                return;
            }

            this.roadConditionModel = pipeline.fit(trainingData);
            System.out.println("Road condition model trained successfully!");

        } catch (Exception e) {
            System.err.println("Error training road condition model: " + e.getMessage());
            e.printStackTrace();
            throw e; // Re-throw to be caught by the caller
        }
    }

    // Create and return the UI component
    public VBox createContent(Stage stage) {
        // Initialize UI components
        rootPane = new VBox(20); // Increased spacing
        rootPane.setStyle("-fx-padding: 30; -fx-background-color: linear-gradient(to bottom, #f0f2f5, #c9d6ff);");

        // Create header
        Label headerLabel = new Label("ML Traffic Prediction");
        headerLabel.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #333333; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0.5, 0, 1);");

        // Create form grid
        GridPane formGrid = new GridPane();
        formGrid.setHgap(15);
        formGrid.setVgap(15);
        formGrid.setPadding(new Insets(20));
        formGrid.setStyle("-fx-background-color: #ffffff; -fx-border-radius: 10; -fx-background-radius: 10; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0.5, 0, 1);");

        // Junction selection
        Label junctionLabel = new Label("Junction:");
        junctionLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #555555;");
        junctionComboBox = new ComboBox<>();
        junctionComboBox.setStyle("-fx-font-size: 14px; -fx-pref-width: 200;");
        formGrid.add(junctionLabel, 0, 0);
        formGrid.add(junctionComboBox, 1, 0);

        // Date selection
        Label dateLabel = new Label("Date:");
        dateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #555555;");
        datePicker = new DatePicker(LocalDate.now());
        datePicker.setStyle("-fx-font-size: 14px; -fx-pref-width: 200;");
        formGrid.add(dateLabel, 0, 1);
        formGrid.add(datePicker, 1, 1);

        // Hour selection
        Label hourLabel = new Label("Hour:");
        hourLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #555555;");
        hourComboBox = new ComboBox<>();
        hourComboBox.setStyle("-fx-font-size: 14px; -fx-pref-width: 200;");
        for (int i = 0; i < 24; i++) {
            hourComboBox.getItems().add(i);
        }
        hourComboBox.setValue(12); // Default to noon
        formGrid.add(hourLabel, 0, 2);
        formGrid.add(hourComboBox, 1, 2);

        // Predict button
        predictButton = new Button("Generate Prediction");
        predictButton.setStyle("-fx-font-size: 16px; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 5;");
        predictButton.setOnMouseEntered(e -> predictButton.setStyle("-fx-font-size: 16px; -fx-background-color: #45a049; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 5;"));
        predictButton.setOnMouseExited(e -> predictButton.setStyle("-fx-font-size: 16px; -fx-background-color: #4CAF50; -fx-text-fill: white; -fx-padding: 10 20; -fx-background-radius: 5;"));
        formGrid.add(predictButton, 1, 3);

        // Results area
        resultArea = new TextArea();
        resultArea.setEditable(false);
        resultArea.setPrefHeight(200);
        resultArea.setStyle("-fx-font-size: 14px; -fx-background-color: #f9f9f9; -fx-border-color: #cccccc; -fx-border-radius: 5; -fx-padding: 10;");

        // Build the layout
        rootPane.getChildren().addAll(headerLabel, formGrid, resultArea);
        rootPane.setAlignment(Pos.CENTER);

        // Set up event handlers
        predictButton.setOnAction(e -> {
            try {
                String junction = junctionComboBox.getValue();
                LocalDate date = datePicker.getValue();
                int hour = hourComboBox.getValue();

                if (junction == null || date == null) {
                    showResult("Please fill in all fields.");
                    return;
                }

                generatePredictions(junction, date, hour);
            } catch (Exception ex) {
                System.err.println("Error generating prediction: " + ex.getMessage());
                ex.printStackTrace();
                showResult("Error generating prediction: " + ex.getMessage());
            }
        });

        // Populate UI components with data
        populateJunctions();

        // Check if models are ready
        if (!modelsReady) {
            showResult("Models are not ready. Please wait for training to complete.");
            predictButton.setDisable(true);
        }

        return rootPane;
    }

    // Improved junction dropdown population method
    private void populateJunctions() {
        try {
            Platform.runLater(() -> {
                junctionComboBox.getItems().clear();
                junctionComboBox.setPromptText("Loading junctions...");
            });

            if (trafficData == null) {
                System.err.println("Traffic data is null when populating junctions");
                Platform.runLater(() -> {
                    junctionComboBox.getItems().add("Error loading data");
                    junctionComboBox.setValue("Error loading data");
                });
                return;
            }

            List<String> junctions = new ArrayList<>();

            // Fetch distinct junction names and ensure they are valid
            trafficData.select("Junction").distinct().collectAsList().forEach(row -> {
                if (!row.isNullAt(0)) {
                    String junction = row.getString(0);
                    if (junction != null && !junction.trim().isEmpty()) {
                        junctions.add(junction);
                    }
                }
            });

            // Sort the junctions alphabetically
            junctions.sort(String::compareTo);

            Platform.runLater(() -> {
                junctionComboBox.getItems().clear();
                if (junctions.isEmpty()) {
                    junctionComboBox.getItems().add("No junctions found");
                    junctionComboBox.setValue("No junctions found");
                } else {
                    junctionComboBox.getItems().addAll(junctions);
                    junctionComboBox.setValue(junctions.get(0));
                }
            });

        } catch (Exception e) {
            System.err.println("Error while populating junctions: " + e.getMessage());
            Platform.runLater(() -> {
                junctionComboBox.getItems().clear();
                junctionComboBox.getItems().add("Error loading data");
                junctionComboBox.setValue("Error loading data");
            });
        }
    }

    private void generatePredictions(String junction, LocalDate date, int hour) {
        if (!modelsReady) {
            showResult("Models are not ready yet. Please wait for training to complete.");
            return;
        }

        try {
            // Format date as string (yyyy-MM-dd)
            String dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // Calculate day of week (1-7, Monday is 1)
            int dayOfWeek = date.getDayOfWeek().getValue();

            // Create a dataframe with the input data
            List<Row> inputData = new ArrayList<>();
            inputData.add(RowFactory.create(junction, dateStr, hour, dayOfWeek));

            StructType schema = new StructType(new StructField[]{
                    DataTypes.createStructField("Junction", DataTypes.StringType, false),
                    DataTypes.createStructField("Date", DataTypes.StringType, false),
                    DataTypes.createStructField("Hour", DataTypes.IntegerType, false),
                    DataTypes.createStructField("DayOfWeek", DataTypes.IntegerType, false)
            });

            Dataset<Row> inputDF = spark.createDataFrame(inputData, schema);

            // Step 1: Predict the number of vehicles
            Dataset<Row> indexedJunctionDF = junctionIndexer.transform(inputDF);

            // Create a separate pipeline for vehicle prediction since we already have junctionIndexer saved
            OneHotEncoder junctionEncoder = new OneHotEncoder()
                    .setInputCol("JunctionIndex")
                    .setOutputCol("JunctionVec");

            VectorAssembler assembler = new VectorAssembler()
                    .setInputCols(new String[]{"Hour", "DayOfWeek", "JunctionVec"})
                    .setOutputCol("features");

            Pipeline vehiclePipeline = new Pipeline().setStages(new PipelineStage[]{
                    junctionEncoder, assembler
            });

            // Transform the input data
            Dataset<Row> processedInput = vehiclePipeline.fit(indexedJunctionDF).transform(indexedJunctionDF);

            // Make vehicle prediction
            Dataset<Row> vehiclePrediction = vehicleModel.transform(processedInput);
            double predictedVehicles = Math.round(vehiclePrediction.select("prediction").first().getDouble(0));

            // Step 2: Use predicted vehicle count to predict road condition
            List<Row> roadConditionInput = new ArrayList<>();
            roadConditionInput.add(RowFactory.create(junction, dateStr, hour, dayOfWeek, predictedVehicles));

            StructType roadConditionSchema = new StructType(new StructField[]{
                    DataTypes.createStructField("Junction", DataTypes.StringType, false),
                    DataTypes.createStructField("Date", DataTypes.StringType, false),
                    DataTypes.createStructField("Hour", DataTypes.IntegerType, false),
                    DataTypes.createStructField("DayOfWeek", DataTypes.IntegerType, false),
                    DataTypes.createStructField("Vehicles", DataTypes.DoubleType, false)
            });

            Dataset<Row> roadConditionInputDF = spark.createDataFrame(roadConditionInput, roadConditionSchema);

            // Apply the road condition model pipeline
            Dataset<Row> roadConditionPrediction = roadConditionModel.transform(roadConditionInputDF);

            // Extract the predicted road condition
            String predictedRoadCondition = roadConditionPrediction.select("predictedRoadCondition").first().getString(0);

            // Format and display the results
            StringBuilder resultBuilder = new StringBuilder();
            resultBuilder.append("Prediction Results:\n\n");
            resultBuilder.append("Junction: ").append(junction).append("\n");
            resultBuilder.append("Date: ").append(dateStr).append("\n");
            resultBuilder.append("Hour: ").append(hour).append(":00\n\n");
            resultBuilder.append("Predicted Vehicle Count: ").append(String.format("%.0f", predictedVehicles)).append("\n");
            resultBuilder.append("Predicted Road Condition: ").append(predictedRoadCondition).append("\n");

            showResult(resultBuilder.toString());

        } catch (Exception e) {
            System.err.println("Error generating predictions: " + e.getMessage());
            e.printStackTrace();
            showResult("Error generating predictions: " + e.getMessage());
        }
    }

    private void showResult(String message) {
        Platform.runLater(() -> {
            resultArea.setText(message);
        });
    }

    // Method to handle cleanup when the page is closed
    public void cleanup() {
        // Clear cached data if needed
        if (trafficData != null) {
            trafficData.unpersist();
        }
    }

    // Additional utility methods
    public boolean areModelsReady() {
        return modelsReady;
    }

    public void retrainModels() {
        // This method could be called from outside to retrain models with new data
        prepareAndTrainModels();
    }

    // Method to update the dataset
    public void updateDataset(Dataset<Row> newData) {
        if (newData != null) {
            // Unpersist old data if it exists
            if (this.trafficData != null) {
                this.trafficData.unpersist();
            }

            // Store and persist new data
            this.trafficData = newData.persist(StorageLevel.MEMORY_AND_DISK());

            // Retrain models with new data
            modelsReady = false;
            prepareAndTrainModels();

            // Update UI components
            populateJunctions();
        }
    }
}