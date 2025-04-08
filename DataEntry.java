package example;

import javafx.beans.property.*;
import java.time.LocalDate;

public class DataEntry {
    private final ObjectProperty<LocalDate> date;
    private final IntegerProperty time;
    private final IntegerProperty vehicleCount;

    public DataEntry(LocalDate date, int time, int vehicleCount) {
        this.date = new SimpleObjectProperty<>(date);
        this.time = new SimpleIntegerProperty(time);
        this.vehicleCount = new SimpleIntegerProperty(vehicleCount);
    }

    // ✅ Getters for Property Bindings
    public ObjectProperty<LocalDate> dateProperty() {
        return date;
    }

    public IntegerProperty timeProperty() {
        return time;
    }

    public IntegerProperty vehicleCountProperty() {
        return vehicleCount;
    }

    // ✅ Convenience Getters
    public LocalDate getDate() {
        return date.get();
    }

    public int getTime() {
        return time.get();
    }

    public int getVehicleCount() {
        return vehicleCount.get();
    }

    // ✅ Convenience Setters
    public void setDate(LocalDate date) {
        this.date.set(date);
    }

    public void setTime(int time) {
        this.time.set(time);
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount.set(vehicleCount);
    }
}
