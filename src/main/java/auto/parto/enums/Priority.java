package auto.parto.enums;

import lombok.Getter;

@Getter
public enum Priority {
    LOW(1, "Low", "gray"),
    NORMAL(2, "Normal", "blue"),
    MEDIUM(3 , "Medium" , "medium"),
    HIGH(3, "High", "orange"),
    URGENT(4, "Urgent", "red");

    private final int level;
    private final String label;
    private final String color;

    Priority(int level, String label, String color) {
        this.level = level;
        this.label = label;
        this.color = color;
    }

    public boolean isHigherThan(Priority other) {
        return this.level > other.level;
    }
}
