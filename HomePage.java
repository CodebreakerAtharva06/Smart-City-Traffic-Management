package example;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.Reflection;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.io.File;

public class HomePage extends Application {

    public static void show(Stage primaryStage) {
        new HomePage().start(primaryStage);
    }

    @Override
    public void start(Stage primaryStage) {
        // Load background image (local first, fallback to online)
        ImageView background = new ImageView();
        String localPath = "D:/3rd year/6th sem/BDT/Mini Project/background2.jpg";
        
        if (new File(localPath).exists()) {
            background.setImage(new Image("file:/" + localPath.replace(" ", "%20")));
        } else {
            // Fallback to a gradient background if image doesn't exist
            background.setImage(new Image(localPath, true));
        }

        background.setFitWidth(1000);
        background.setFitHeight(700);
        
        // Apply a slight blur effect to the background for depth
        GaussianBlur blur = new GaussianBlur(5);
        background.setEffect(blur);
        
        // Create a dark overlay to increase contrast with text
        Rectangle overlay = new Rectangle(1000, 700);
        overlay.setFill(Color.rgb(0, 0, 0, 0.6));
        
        // Header section with logo and title
        HBox header = createHeader();
        
        // Main content area
        VBox contentArea = createContentArea(primaryStage);
        
        // Footer with additional info
        HBox footer = createFooter();
        
        // Main layout using BorderPane for better organization
        BorderPane mainLayout = new BorderPane();
        mainLayout.setTop(header);
        mainLayout.setCenter(contentArea);
        mainLayout.setBottom(footer);
        
        // Root layout with background and content
        StackPane root = new StackPane();
        root.getChildren().addAll(background, overlay, mainLayout);
        
        // Scene setup with larger dimensions for modern displays
        Scene homeScene = new Scene(root, 1000, 700);
        primaryStage.setTitle("Smart City Traffic Management");
        primaryStage.setScene(homeScene);
        primaryStage.setResizable(true);
        primaryStage.show();
    }
    
    private HBox createHeader() {
        // Header with logo and title
        HBox header = new HBox(20);
        header.setPadding(new Insets(30, 40, 10, 40));
        header.setAlignment(Pos.CENTER_LEFT);
        
        // Logo (placeholder - replace with actual logo)
        Text logoText = new Text("🌆");
        logoText.setFont(Font.font("Arial", FontWeight.BOLD, 36));
        logoText.setFill(Color.WHITE);
        
        // Title with gradient text effect
        Text title = new Text("SMART CITY");
        title.setFont(Font.font("Montserrat", FontWeight.BOLD, 42));
        title.setFill(Color.WHITE);
        
        Text subtitle = new Text("TRAFFIC MANAGEMENT SYSTEM");
        subtitle.setFont(Font.font("Montserrat", FontWeight.BOLD, 18));
        subtitle.setFill(Color.rgb(46, 204, 113));
        
        // Create a vertical box for title and subtitle
        VBox titleBox = new VBox(5, title, subtitle);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        
        // Apply reflection effect to the title
        Reflection reflection = new Reflection();
        reflection.setFraction(0.2);
        titleBox.setEffect(reflection);
        
        header.getChildren().addAll(logoText, titleBox);
        return header;
    }
    
    private VBox createContentArea(Stage primaryStage) {
        // Main content section
        VBox contentArea = new VBox(40);
        contentArea.setAlignment(Pos.CENTER);
        contentArea.setPadding(new Insets(20, 40, 40, 40));
        
        // Tagline with shadow effect
        Text tagline = new Text("Real-time Traffic Monitoring & Intelligent Analytics");
        tagline.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        tagline.setFill(Color.WHITE);
        tagline.setTextAlignment(TextAlignment.CENTER);
        
        DropShadow taglineShadow = new DropShadow();
        taglineShadow.setColor(Color.rgb(52, 152, 219, 0.7));
        taglineShadow.setRadius(15);
        tagline.setEffect(taglineShadow);
        
        // Descriptive text
        Text description = new Text("Transforming urban mobility through advanced data analytics and machine learning. Monitor traffic patterns, predict congestion, and optimize flow in real-time.");
        description.setFont(Font.font("Arial", 16));
        description.setFill(Color.LIGHTGRAY);
        description.setTextAlignment(TextAlignment.CENTER);
        description.setWrappingWidth(700);
        
        // Navigation buttons in horizontal layout for better space utilization
        HBox buttonsBox = createButtonsBox(primaryStage);
        
        // Add elements to content area
        contentArea.getChildren().addAll(tagline, description, buttonsBox);
        return contentArea;
    }
    
    private HBox createButtonsBox(Stage primaryStage) {
        // Analytics button with icon and improved styling
        Button analyticsBtn = createStyledButton("📊 Traffic Analytics", 
                "Analyze historical and real-time traffic data", 
                e -> AnalyticsPage.show(primaryStage),
                Color.rgb(52, 152, 219), Color.rgb(41, 128, 185));
        
        // ML Analysis button with icon and description
        Button mlAnalysisBtn = createStyledButton("🤖 ML Predictions", 
                "AI-powered traffic forecasting", 
                e -> {
                    try {
                        MLPredictedAnalysisPage mlPage = new MLPredictedAnalysisPage();
                        VBox mlContent = mlPage.createContent(primaryStage);
                        Scene mlScene = new Scene(mlContent, 1000, 700);
                        primaryStage.setScene(mlScene);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.err.println("Error: MLPredictedAnalysisPage could not be loaded.");
                    }
                },
                Color.rgb(46, 204, 113), Color.rgb(39, 174, 96));
        
        // Dashboard button with icon and description
        Button dashboardBtn = createStyledButton("🚦 Live Dashboard", 
                "Monitor traffic in real-time", 
                e -> TrafficDashboard.show(primaryStage),
                Color.rgb(155, 89, 182), Color.rgb(142, 68, 173));
        
        // Horizontal layout for buttons with spacing
        HBox buttonsBox = new HBox(30);
        buttonsBox.setAlignment(Pos.CENTER);
        buttonsBox.setPadding(new Insets(20, 0, 0, 0));
        buttonsBox.getChildren().addAll(analyticsBtn, mlAnalysisBtn, dashboardBtn);
        
        return buttonsBox;
    }
    
    private HBox createFooter() {
        // Simple footer with copyright information
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(20, 0, 30, 0));
        
        Text footerText = new Text("© 2025 Smart City Traffic Management");
        footerText.setFont(Font.font("Arial", 12));
        footerText.setFill(Color.LIGHTGRAY);
        
        footer.getChildren().add(footerText);
        return footer;
    }

    // Enhanced button styling with description and hover effects
    private Button createStyledButton(String title, String description, 
                                     javafx.event.EventHandler<javafx.event.ActionEvent> event,
                                     Color primaryColor, Color secondaryColor) {
        Text titleText = new Text(title);
        titleText.setFill(Color.WHITE);
        
        Text descText = new Text(description);
        descText.setFont(Font.font("Arial", 14));
        descText.setFill(Color.WHITE);
        
        VBox buttonContent = new VBox(10, titleText, descText);
        buttonContent.setAlignment(Pos.CENTER);
        
        Button button = new Button();
        button.setGraphic(buttonContent);
        button.setPrefSize(220, 150);
        
        // Button styling with gradient background and rounded corners
        String normalStyle = String.format(
                "-fx-background-color: linear-gradient(to bottom, rgb(%d,%d,%d), rgb(%d,%d,%d)); " +
                "-fx-background-radius: 15px; " +
                "-fx-padding: 15px; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 10, 0, 0, 5);",
                (int)primaryColor.getRed()*255, (int)primaryColor.getGreen()*255, (int)primaryColor.getBlue()*255,
                (int)secondaryColor.getRed()*255, (int)secondaryColor.getGreen()*255, (int)secondaryColor.getBlue()*255);
        
        String hoverStyle = String.format(
                "-fx-background-color: linear-gradient(to bottom, rgb(%d,%d,%d), rgb(%d,%d,%d)); " +
                "-fx-background-radius: 15px; " +
                "-fx-padding: 15px; " +
                "-fx-scale-x: 1.05; -fx-scale-y: 1.05; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 20, 0, 0, 10);",
                (int)secondaryColor.getRed()*255, (int)secondaryColor.getGreen()*255, (int)secondaryColor.getBlue()*255,
                (int)primaryColor.getRed()*255, (int)primaryColor.getGreen()*255, (int)primaryColor.getBlue()*255);
        
        button.setStyle(normalStyle);
        button.setOnMouseEntered(e -> button.setStyle(hoverStyle));
        button.setOnMouseExited(e -> button.setStyle(normalStyle));
        button.setOnAction(event);
        
        return button;
    }

    public static void main(String[] args) {
        launch(args);
    }
}